import SwiftUI

private func ago(_ timestamp: String) -> String {
    let date = chatDate(timestamp)
    return Date().timeIntervalSince(date) < 60 ? "just now" : RelativeDateTimeFormatter().localizedString(for: date, relativeTo: Date())
}

private struct Note: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View { Text(text).foregroundStyle(TroopTheme.muted).fixedSize(horizontal: false, vertical: true) }
}

extension View {
    /// Shows `message` in an alert while it is non-nil.
    func chatAlert(_ message: Binding<String?>) -> some View {
        alert(message.wrappedValue ?? "", isPresented: Binding(get: { message.wrappedValue != nil }, set: { if !$0 { message.wrappedValue = nil } })) {
            Button("OK", role: .cancel) {}
        }
    }
}

struct ChatView: View {
    enum Phase { case loading, signedOut, failed(String), noProfile, waiting(Member), ready(Member, [ChatGroup], [Member]) }
    @State private var phase = Phase.loading
    @State private var newGroup = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Troop Chat").font(.largeTitle.bold()).foregroundStyle(TroopTheme.forest)
                switch phase {
                case .loading: ProgressView().frame(maxWidth: .infinity)
                case .signedOut: SignInForm { await load() }
                case .failed(let message):
                    Note(message)
                    Button("Try again") { Task { await load() } }.buttonStyle(.borderedProminent)
                    AccountLinks { await load() }
                case .noProfile:
                    Note("Finish setting up your account.")
                    ProfileForm { await load() }
                    AccountLinks { await load() }
                case .waiting(let me):
                    Note("Thanks, \(me.name)! A troop leader needs to approve your account before you can chat.")
                    Button("Check again") { Task { await load() } }.buttonStyle(.borderedProminent)
                    AccountLinks { await load() }
                case .ready(let me, let groups, let all):
                    if me.leader {
                        let waiting = all.filter { !$0.approved }.count
                        NavigationLink(value: GuideRoute.chatMembers) {
                            Label("Members & approvals" + (waiting > 0 ? "  (\(waiting) waiting)" : ""), systemImage: "person.2.fill")
                        }.buttonStyle(.bordered).accessibilityIdentifier("chat-members")
                    }
                    Button { newGroup = true } label: { Label("New group", systemImage: "plus").frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent).accessibilityIdentifier("new-group")
                    if groups.isEmpty { Note("No groups yet. Start one for your patrol!") }
                    ForEach(groups) { g in
                        let member = g.members.contains(me.id)
                        NavigationLink(value: GuideRoute.chatGroup(g.id)) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text((member && Chat.isUnread(g) ? "● " : "") + g.name).font(.headline).foregroundStyle(TroopTheme.ink)
                                Text("\(g.members.count) members · \(ago(g.lastAt))" + (member ? "" : " · leader view")).font(.subheadline).foregroundStyle(TroopTheme.muted)
                            }.frame(maxWidth: .infinity, alignment: .leading).troopCard()
                        }.buttonStyle(.plain)
                    }
                    AccountLinks { await load() }
                }
            }.padding(20).frame(maxWidth: 800).frame(maxWidth: .infinity)
        }
        .background(TroopTheme.paper).navigationTitle("Troop Chat").navigationBarTitleDisplayMode(.inline)
        .task { await load() } // reruns when coming back from a group
        .refreshable { await load() }
        .sheet(isPresented: $newGroup, onDismiss: { Task { await load() } }) { NavigationStack { GroupEditView(id: nil) } }
    }

    private func load() async {
        guard Chat.uid != nil else { phase = .signedOut; return }
        do {
            guard let me = try await Chat.me() else { phase = .noProfile; return }
            guard me.approved else { phase = .waiting(me); return }
            phase = .ready(me, try await Chat.groups(me), me.leader ? try await Chat.members() : [])
        } catch {
            phase = Chat.uid == nil ? .signedOut : .failed(error.localizedDescription)
        }
    }
}

private struct SignInForm: View {
    let done: () async -> Void
    @State private var email = ""
    @State private var password = ""
    @State private var creating = false
    @State private var name = ""
    @State private var role: String?
    @State private var busy = false
    @State private var alert: String?
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Note("Chat with your troop. A leader approves every new account, and leaders can read every group.")
            TextField("Email", text: $email).keyboardType(.emailAddress).textContentType(.username)
                .textInputAutocapitalization(.never).autocorrectionDisabled().troopCard().accessibilityIdentifier("chat-email")
            SecureField("Password (6 or more characters)", text: $password).textContentType(creating ? .newPassword : .password)
                .troopCard().accessibilityIdentifier("chat-password")
            if creating {
                ProfileFields(name: $name, role: $role)
                Note("Scouts under 13: ask a parent to set up your account with you.")
            }
            Button { Task { await submit() } } label: { Text(creating ? "Create account" : "Sign in").frame(maxWidth: .infinity) }
                .buttonStyle(.borderedProminent).controlSize(.large).disabled(busy).accessibilityIdentifier("chat-submit")
            Button(creating ? "Have an account? Sign in" : "New here? Create an account") { creating.toggle() }
                .frame(maxWidth: .infinity).accessibilityIdentifier("chat-toggle")
            Button("Forgot password?") { Task { await forgot() } }.frame(maxWidth: .infinity)
        }.chatAlert($alert)
    }

    private func submit() async {
        let email = email.trimmingCharacters(in: .whitespaces)
        let name = name.trimmingCharacters(in: .whitespaces)
        if creating && (name.isEmpty || role == nil) { alert = "Enter your name and choose Scout or Adult."; return }
        busy = true; defer { busy = false }
        do {
            if creating { try await Chat.signUp(email, password, name: name, role: role!) } else { try await Chat.signIn(email, password) }
            ChatNotifier.enable()
            await done()
        } catch {
            alert = error.localizedDescription
            if Chat.uid != nil { await done() } // signed up, but the profile failed: show the profile form
        }
    }

    private func forgot() async {
        let email = email.trimmingCharacters(in: .whitespaces)
        if email.isEmpty { alert = "Enter your email first."; return }
        do { try await Chat.resetPassword(email); alert = "Check your email for a reset link." } catch { alert = error.localizedDescription }
    }
}

private struct ProfileFields: View {
    @Binding var name: String
    @Binding var role: String?
    var body: some View {
        TextField("Your name, as the troop knows you", text: $name).textContentType(.name).textInputAutocapitalization(.words)
            .troopCard().accessibilityIdentifier("chat-name")
            .onChange(of: name) { if $0.count > 40 { name = String($0.prefix(40)) } }
        Picker("I am a", selection: $role) {
            Text("Scout").tag(String?.some("scout"))
            Text("Adult (parent or leader)").tag(String?.some("adult"))
        }.pickerStyle(.segmented)
    }
}

private struct ProfileForm: View {
    let done: () async -> Void
    @State private var name = ""
    @State private var role: String?
    @State private var alert: String?
    var body: some View {
        ProfileFields(name: $name, role: $role)
        Button("Save") {
            let name = name.trimmingCharacters(in: .whitespaces)
            guard !name.isEmpty, let role else { alert = "Enter your name and choose Scout or Adult."; return }
            Task {
                do { try await Chat.createProfile(name: name, role: role); await done() } catch { alert = error.localizedDescription }
            }
        }.buttonStyle(.borderedProminent).chatAlert($alert)
    }
}

private struct AccountLinks: View {
    let done: () async -> Void
    @State private var deleting = false
    @State private var password = ""
    @State private var alert: String?
    var body: some View {
        VStack(spacing: 8) {
            Text("Signed in as \(Chat.email ?? "")").font(.footnote).foregroundStyle(TroopTheme.muted)
            Button("Sign out") { Chat.signOut(); Task { await done() } }
            Button("Delete my account", role: .destructive) { password = ""; deleting = true }
        }
        .frame(maxWidth: .infinity).padding(.top, 20)
        .alert("Delete your chat account?", isPresented: $deleting) {
            SecureField("Your password", text: $password)
            Button("Delete", role: .destructive) {
                Task {
                    do { try await Chat.deleteAccount(password: password); await done() } catch { alert = error.localizedDescription }
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: { Text("Your messages stay in their groups.") }
        .chatAlert($alert)
    }
}

struct ChatGroupView: View {
    let id: String
    @Environment(\.dismiss) private var dismiss
    @State private var me: Member?
    @State private var group: ChatGroup?
    @State private var messages: [ChatMessage] = []
    @State private var status = "Loading…"
    @State private var draft = ""
    @State private var sending = false
    @State private var editing = false
    @State private var leaving = false
    @State private var deleting = false
    @State private var alert: String?

    private var isMember: Bool { group.map { g in me.map { g.members.contains($0.id) } ?? false } ?? false }

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 6) {
                        if group == nil { Note(status).padding(.top) }
                        else if !isMember { Note("Leader view: you can read this group but you are not in it.") }
                        if group != nil && messages.isEmpty { Note("No messages yet. Say hi!").padding(.top) }
                        ForEach(messages) { bubble($0) }
                    }.padding(12).frame(maxWidth: 800).frame(maxWidth: .infinity)
                }
                .onChange(of: messages.last?.id) { last in
                    if let last { withAnimation { proxy.scrollTo(last, anchor: .bottom) } }
                }
            }
            if isMember {
                HStack(alignment: .bottom, spacing: 8) {
                    TextField("Message", text: $draft, axis: .vertical).lineLimit(1...4).troopCard().accessibilityIdentifier("chat-message")
                        .onChange(of: draft) { if $0.count > 2000 { draft = String($0.prefix(2000)) } }
                    Button("Send") { Task { await send() } }.buttonStyle(.borderedProminent).controlSize(.large)
                        .disabled(sending || draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }.padding(10).background(TroopTheme.paper)
            }
        }
        .background(TroopTheme.paper).navigationTitle(group?.name ?? "Loading…").navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if let group, let me {
                    Menu {
                        if isMember || me.leader { Button("Edit name and members") { editing = true } }
                        if isMember { Button("Leave group") { leaving = true } }
                        if me.leader || group.createdBy == me.id { Button("Delete group", role: .destructive) { deleting = true } }
                    } label: { Image(systemName: "ellipsis.circle") }.accessibilityLabel("Group options")
                }
            }
        }
        .confirmationDialog("Leave \(group?.name ?? "")?", isPresented: $leaving, titleVisibility: .visible) {
            Button("Leave", role: .destructive) {
                guard let group, let me else { return }
                act { try await Chat.editGroup(group.id, name: group.name, members: group.members.filter { $0 != me.id }) }
            }
        }
        .confirmationDialog("Delete \(group?.name ?? "") for everyone?", isPresented: $deleting, titleVisibility: .visible) {
            Button("Delete", role: .destructive) { act { try await Chat.deleteGroup(id) } }
        }
        .sheet(isPresented: $editing, onDismiss: { Task { group = try? await Chat.group(id) } }) { NavigationStack { GroupEditView(id: id) } }
        .chatAlert($alert)
        .task { await run() }
    }

    private func bubble(_ msg: ChatMessage) -> some View {
        let mine = msg.uid == Chat.uid
        return HStack {
            if mine { Spacer(minLength: 48) }
            VStack(alignment: .leading, spacing: 2) {
                if !mine { Text(msg.name).font(.caption.bold()).foregroundStyle(TroopTheme.red) }
                Text(msg.text).foregroundStyle(mine ? Color.white : TroopTheme.ink).fixedSize(horizontal: false, vertical: true)
                Text(ago(msg.at)).font(.caption2).foregroundStyle(mine ? Color.white.opacity(0.8) : TroopTheme.muted)
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(mine ? TroopTheme.forest : TroopTheme.card, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(mine ? Color.clear : TroopTheme.line))
            .contextMenu {
                if mine || me?.leader == true {
                    Button("Delete message", role: .destructive) {
                        Task {
                            do { try await Chat.deleteMessage(id, msg.id); messages.removeAll { $0.id == msg.id } }
                            catch { alert = error.localizedDescription }
                        }
                    }
                }
            }
            if !mine { Spacer(minLength: 48) }
        }.id(msg.id)
    }

    private func run() async {
        do {
            let m = try await Chat.me(), g = try await Chat.group(id)
            guard let m, let g else { status = "This group no longer exists."; return }
            me = m; group = g
            Chat.markSeen(g)
            add(try await Chat.messages(id, after: nil))
        } catch { status = error.localizedDescription; return }
        // ponytail: 4 s polling while this screen is open; Firestore's listen stream would need the SDK
        while !Task.isCancelled {
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            if !Task.isCancelled, let new = try? await Chat.messages(id, after: messages.last?.at) { add(new) }
        }
    }

    private func add(_ new: [ChatMessage]) {
        let shown = Set(messages.map(\.id))
        messages += new.filter { !shown.contains($0.id) }
    }

    private func send() async {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let me, !text.isEmpty else { return }
        sending = true; defer { sending = false }
        do {
            try await Chat.send(id, name: me.name, text: text)
            draft = ""
            add(try await Chat.messages(id, after: messages.last?.at))
        } catch { alert = error.localizedDescription }
    }

    private func act(_ work: @escaping () async throws -> Void) {
        Task {
            do { try await work(); dismiss() } catch { alert = error.localizedDescription }
        }
    }
}

struct GroupEditView: View {
    let id: String?
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var people: [Member] = []
    @State private var chosen: Set<String> = []
    @State private var me: Member?
    @State private var saving = false
    @State private var alert: String?

    var body: some View {
        Form {
            Section {
                TextField("Group name, e.g. Eagle Patrol", text: $name).textInputAutocapitalization(.words).accessibilityIdentifier("group-name")
                    .onChange(of: name) { if $0.count > 50 { name = String($0.prefix(50)) } }
            }
            Section {
                if people.isEmpty { ProgressView() }
                ForEach(people) { p in
                    Toggle((p.id == me?.id ? "You (\(p.name))" : p.name) + "  ·  " + (p.leader ? "leader" : p.role), isOn: Binding(
                        get: { chosen.contains(p.id) },
                        set: { if $0 { chosen.insert(p.id) } else { chosen.remove(p.id) } }))
                }
            } header: { Text("Who's in it") } footer: { Text("Leaders can read every group. A group can't be just one adult and one scout.") }
        }
        .navigationTitle(id == nil ? "New group" : "Edit group").navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) { Button(id == nil ? "Create" : "Save") { Task { await save() } }.disabled(saving || me == nil) }
        }
        .chatAlert($alert)
        .task { await load() }
    }

    private func load() async {
        do {
            guard let m = try await Chat.me() else { throw ChatError(message: "Please sign in again.") }
            let all = try await Chat.members().filter(\.approved)
            var g: ChatGroup?
            if let id { g = try await Chat.group(id) }
            me = m
            if let g { name = g.name }
            chosen = Set(g?.members ?? [m.id])
            people = all.sorted { ($0.id == m.id ? 0 : 1, $0.name.lowercased()) < ($1.id == m.id ? 0 : 1, $1.name.lowercased()) }
        } catch { alert = error.localizedDescription }
    }

    private func save() async {
        guard let me else { return }
        let name = name.trimmingCharacters(in: .whitespaces)
        let roles = Set(chosen.compactMap { id in people.first { $0.id == id }?.role })
        if name.isEmpty { alert = "Give the group a name."; return }
        if chosen.isEmpty { alert = "Pick at least one person."; return }
        if id == nil && !chosen.contains(me.id) { alert = "Include yourself in a new group."; return }
        if chosen.count == 2 && roles.count == 2 { alert = "Add a third person. One adult and one scout alone isn't allowed."; return }
        saving = true; defer { saving = false }
        do {
            if let id { try await Chat.editGroup(id, name: name, members: Array(chosen)) } else { try await Chat.createGroup(name: name, members: Array(chosen)) }
            dismiss()
        } catch { alert = error.localizedDescription }
    }
}

struct ChatMembersView: View {
    @State private var all: [Member]?
    @State private var removing: Member?
    @State private var alert: String?
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 10) {
                Text("Members").font(.largeTitle.bold()).foregroundStyle(TroopTheme.forest)
                Note("Approve only people you know from the troop. For scouts under 13, confirm a parent agreed.")
                if all == nil { ProgressView().frame(maxWidth: .infinity) }
                ForEach(all ?? []) { p in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(p.name).font(.headline).foregroundStyle(TroopTheme.ink)
                        Text([p.role, p.leader ? "leader" : nil, p.approved ? nil : "waiting for approval"].compactMap { $0 }.joined(separator: " · "))
                            .font(.subheadline).foregroundStyle(p.approved ? TroopTheme.muted : TroopTheme.red)
                        if p.id != Chat.uid {
                            HStack {
                                if !p.approved { Button("Approve") { act { try await Chat.setUser(p.id, approved: true, leader: false) } } }
                                if p.approved && p.role == "adult" {
                                    Button(p.leader ? "Remove leader" : "Make leader") { act { try await Chat.setUser(p.id, approved: true, leader: !p.leader) } }
                                }
                                Button("Remove", role: .destructive) { removing = p }
                            }.buttonStyle(.bordered)
                        }
                    }.frame(maxWidth: .infinity, alignment: .leading).troopCard()
                }
            }.padding(20).frame(maxWidth: 800).frame(maxWidth: .infinity)
        }
        .background(TroopTheme.paper).navigationTitle("Members").navigationBarTitleDisplayMode(.inline)
        .confirmationDialog("Remove \(removing?.name ?? "") from troop chat?", isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } }), titleVisibility: .visible) {
            Button("Remove", role: .destructive) { if let p = removing { act { try await Chat.removeUser(p.id) } } }
        }
        .chatAlert($alert)
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        do { all = try await Chat.members().sorted { ($0.approved ? 1 : 0, $0.name.lowercased()) < ($1.approved ? 1 : 0, $1.name.lowercased()) } }
        catch { alert = error.localizedDescription }
    }

    private func act(_ work: @escaping () async throws -> Void) {
        Task {
            do { try await work(); await load() } catch { alert = error.localizedDescription }
        }
    }
}

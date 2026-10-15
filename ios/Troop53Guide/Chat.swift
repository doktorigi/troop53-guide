import Foundation

typealias JSON = [String: Any]

struct Member: Identifiable, Hashable { let id: String; let name: String; let role: String; let approved: Bool; let leader: Bool }
struct ChatGroup: Identifiable, Hashable { let id: String; let name: String; let members: [String]; let createdBy: String; let lastAt: String }
struct ChatMessage: Identifiable, Hashable { let id: String; let uid: String; let name: String; let text: String; let at: String }

struct ChatError: LocalizedError {
    let message: String
    var code = 0
    var errorDescription: String? { message }
}

/// Firestore timestamps carry up to nanoseconds; whole seconds are enough for "5 minutes ago".
func chatDate(_ timestamp: String) -> Date {
    let whole = timestamp.split(separator: ".").first.map { String($0) + ($0.hasSuffix("Z") ? "" : "Z") } ?? ""
    return ISO8601DateFormatter().date(from: whole) ?? .distantPast
}

/// A Firestore REST document.
struct FirestoreDoc {
    let id: String
    private let fields: JSON
    init(_ json: JSON) {
        id = (json["name"] as? String)?.components(separatedBy: "/").last ?? ""
        fields = json["fields"] as? JSON ?? [:]
    }
    func s(_ key: String) -> String {
        let value = fields[key] as? JSON
        return value?["stringValue"] as? String ?? value?["timestampValue"] as? String ?? ""
    }
    func b(_ key: String) -> Bool { (fields[key] as? JSON)?["booleanValue"] as? Bool ?? false }
    func list(_ key: String) -> [String] {
        (((fields[key] as? JSON)?["arrayValue"] as? JSON)?["values"] as? [JSON] ?? []).compactMap { $0["stringValue"] as? String }
    }
    var member: Member { Member(id: id, name: s("name"), role: s("role"), approved: b("approved"), leader: b("leader")) }
    var group: ChatGroup { ChatGroup(id: id, name: s("name"), members: list("members"), createdBy: s("createdBy"), lastAt: s("lastAt")) }
    var message: ChatMessage { ChatMessage(id: id, uid: s("uid"), name: s("name"), text: s("text"), at: s("at")) }
}

/// Troop chat on Firebase Auth + Firestore over plain REST, same as Android's Chat.kt, so the app keeps zero dependencies.
/// Every permission check lives in firestore.rules; this client is untrusted.
enum Chat {
    static let project = "troop53-guide"
    private static let auth = "https://identitytoolkit.googleapis.com/v1"
    private static let docName = "projects/\(project)/databases/(default)/documents/"
    private static let docs = "https://firestore.googleapis.com/v1/projects/\(project)/databases/(default)/documents"
    private static let sessionKey = "troop53.chat"
    private static let seenKey = "troop53.chatSeen"

    /// The Firebase web API key, read from the Android google-services.json bundled as a resource (one source of truth).
    private static let apiKey: String = {
        guard let url = Bundle.main.url(forResource: "google-services", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let json = try? JSONSerialization.jsonObject(with: data) as? JSON,
              let client = (json["client"] as? [JSON])?.first,
              let key = (client["api_key"] as? [JSON])?.first?["current_key"] as? String else { return "" }
        return key
    }()

    // ponytail: session (incl. refresh token) in app-sandboxed UserDefaults, same as Android prefs; move to Keychain before App Store
    private static var session: JSON { UserDefaults.standard.dictionary(forKey: sessionKey) ?? [:] }
    private static func save(_ changes: JSON) { UserDefaults.standard.set(session.merging(changes) { $1 }, forKey: sessionKey) }
    static var uid: String? { session["uid"] as? String }
    static var email: String? { session["email"] as? String }

    // MARK: account

    static func signIn(_ email: String, _ password: String) async throws { try await authenticate("accounts:signInWithPassword", email, password) }

    static func signUp(_ email: String, _ password: String, name: String, role: String) async throws {
        try await authenticate("accounts:signUp", email, password)
        try await createProfile(name: name, role: role)
    }

    static func createProfile(name: String, role: String) async throws {
        try await commit([create("users/\(uid ?? "")", fields(["name": name, "role": role, "approved": false, "leader": false]))])
    }

    static func resetPassword(_ email: String) async throws {
        _ = try await call("POST", "\(auth)/accounts:sendOobCode?key=\(apiKey)", ["requestType": "PASSWORD_RESET", "email": email])
    }

    static func signOut() { UserDefaults.standard.removeObject(forKey: sessionKey); UserDefaults.standard.removeObject(forKey: seenKey) }

    /// Firebase only deletes accounts after a fresh sign-in, so this re-checks the password first.
    static func deleteAccount(password: String) async throws {
        try await signIn(email ?? "", password)
        try await delete("users/\(uid ?? "")")
        _ = try await call("POST", "\(auth)/accounts:delete?key=\(apiKey)", ["idToken": try await token()])
        signOut()
    }

    private static func authenticate(_ endpoint: String, _ email: String, _ password: String) async throws {
        let r = try await call("POST", "\(auth)/\(endpoint)?key=\(apiKey)", ["email": email, "password": password, "returnSecureToken": true]) as? JSON ?? [:]
        save(["uid": r["localId"] as? String ?? "", "email": email])
        saveToken(r["idToken"], r["refreshToken"], r["expiresIn"])
    }

    private static func saveToken(_ id: Any?, _ refresh: Any?, _ expiresIn: Any?) {
        let seconds = Double(expiresIn as? String ?? "") ?? 3600
        save(["id": id as? String ?? "", "refresh": refresh as? String ?? "", "exp": Date().timeIntervalSince1970 + seconds])
    }

    private static func token() async throws -> String {
        if let id = session["id"] as? String, Date().timeIntervalSince1970 < (session["exp"] as? Double ?? 0) - 60 { return id }
        guard let refresh = session["refresh"] as? String else { throw ChatError(message: "Please sign in.") }
        let r: JSON
        do {
            r = try await call("POST", "https://securetoken.googleapis.com/v1/token?key=\(apiKey)",
                form: "grant_type=refresh_token&refresh_token=" + (refresh.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? "")) as? JSON ?? [:]
        } catch let e as ChatError {
            if (400..<500).contains(e.code) { signOut() } // account deleted or disabled
            throw e
        }
        saveToken(r["id_token"], r["refresh_token"], r["expires_in"])
        return r["id_token"] as? String ?? ""
    }

    // MARK: people

    static func me() async throws -> Member? {
        guard let uid else { return nil }
        return try await get("users/\(uid)")?.member
    }

    // ponytail: one page of 300 users is plenty for a troop
    static func members() async throws -> [Member] {
        let r = try await call("GET", "\(docs)/users?pageSize=300", token: try await token()) as? JSON
        return (r?["documents"] as? [JSON] ?? []).map { FirestoreDoc($0).member }
    }

    static func setUser(_ uid: String, approved: Bool, leader: Bool) async throws {
        try await commit([update("users/\(uid)", fields(["approved": approved, "leader": leader]))])
    }

    static func removeUser(_ uid: String) async throws { try await delete("users/\(uid)") }

    // MARK: groups

    static func group(_ id: String) async throws -> ChatGroup? { try await get("groups/\(id)")?.group }

    /// Leaders see every group (Youth Protection oversight); everyone else sees groups they belong to.
    static func groups(_ me: Member) async throws -> [ChatGroup] {
        var q = from("groups")
        if !me.leader { q["where"] = filter("members", "ARRAY_CONTAINS", enc(me.id)) }
        return try await query("", q).map(\.group).sorted { chatDate($0.lastAt) > chatDate($1.lastAt) }
    }

    static func createGroup(name: String, members: [String]) async throws {
        try await commit([create("groups/\(newID())", fields(["name": name, "members": members, "createdBy": uid ?? ""]), timeField: "lastAt")])
    }

    static func editGroup(_ id: String, name: String, members: [String]) async throws {
        try await commit([update("groups/\(id)", fields(["name": name, "members": members]))])
    }

    // ponytail: messages of a deleted group are orphaned (unreadable by rules); add cleanup if storage ever matters
    static func deleteGroup(_ id: String) async throws { try await delete("groups/\(id)") }

    // MARK: messages

    /// The newest `latest` messages, or everything after `after` (a server timestamp), oldest first.
    static func messages(_ groupID: String, after: String?, latest: Int = 100) async throws -> [ChatMessage] {
        var q = from("messages")
        if let after { q["where"] = filter("at", "GREATER_THAN", ["timestampValue": after]) }
        q["orderBy"] = [["field": ["fieldPath": "at"], "direction": after == nil ? "DESCENDING" : "ASCENDING"] as JSON]
        if after == nil { q["limit"] = latest }
        let msgs = try await query("groups/\(groupID)", q).map(\.message)
        return after == nil ? msgs.reversed() : msgs
    }

    static func send(_ groupID: String, name: String, text: String) async throws {
        try await commit([
            create("groups/\(groupID)/messages/\(newID())", fields(["uid": uid ?? "", "name": name, "text": text]), timeField: "at"),
            ["transform": ["document": docName + "groups/\(groupID)", "fieldTransforms": serverTime("lastAt")] as JSON],
        ])
    }

    static func deleteMessage(_ groupID: String, _ id: String) async throws { try await delete("groups/\(groupID)/messages/\(id)") }

    // MARK: seen (unread dots and notifications)

    static func isUnread(_ g: ChatGroup) -> Bool { (UserDefaults.standard.dictionary(forKey: seenKey)?[g.id] as? String) != g.lastAt }
    static func markSeen(_ g: ChatGroup) {
        var seen = UserDefaults.standard.dictionary(forKey: seenKey) ?? [:]
        seen[g.id] = g.lastAt
        UserDefaults.standard.set(seen, forKey: seenKey)
    }

    // MARK: Firestore REST plumbing


    private static func get(_ path: String) async throws -> FirestoreDoc? {
        do {
            return FirestoreDoc(try await call("GET", "\(docs)/\(path)", token: try await token()) as? JSON ?? [:])
        } catch let e as ChatError where e.code == 404 { return nil }
    }

    private static func delete(_ path: String) async throws { _ = try await call("DELETE", "\(docs)/\(path)", token: try await token()) }

    private static func query(_ parent: String, _ q: JSON) async throws -> [FirestoreDoc] {
        let r = try await call("POST", "\(docs)\(parent.isEmpty ? "" : "/" + parent):runQuery", ["structuredQuery": q], token: try await token())
        return (r as? [JSON] ?? []).compactMap { ($0["document"] as? JSON).map(FirestoreDoc.init) }
    }

    private static func commit(_ writes: [JSON]) async throws {
        _ = try await call("POST", "\(docs):commit", ["writes": writes], token: try await token())
    }

    private static func create(_ path: String, _ f: JSON, timeField: String? = nil) -> JSON {
        var w: JSON = ["update": ["name": docName + path, "fields": f] as JSON, "currentDocument": ["exists": false]]
        if let timeField { w["updateTransforms"] = serverTime(timeField) }
        return w
    }

    private static func update(_ path: String, _ f: JSON) -> JSON {
        ["update": ["name": docName + path, "fields": f] as JSON, "currentDocument": ["exists": true], "updateMask": ["fieldPaths": Array(f.keys)]]
    }

    private static func serverTime(_ field: String) -> [JSON] { [["fieldPath": field, "setToServerValue": "REQUEST_TIME"]] }
    private static func from(_ collection: String) -> JSON { ["from": [["collectionId": collection]]] }
    private static func filter(_ field: String, _ op: String, _ value: JSON) -> JSON {
        ["fieldFilter": ["field": ["fieldPath": field], "op": op, "value": value] as JSON]
    }
    private static func newID() -> String { UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased() }

    static func enc(_ value: Any) -> JSON {
        switch value {
        case let s as String: return ["stringValue": s]
        case let b as Bool: return ["booleanValue": b]
        case let a as [String]: return ["arrayValue": ["values": a.map(enc)]]
        default: preconditionFailure("Unsupported Firestore value \(value)")
        }
    }

    private static func fields(_ pairs: JSON) -> JSON { pairs.mapValues(enc) }

    private static func call(_ method: String, _ url: String, _ body: JSON? = nil, token: String? = nil, form: String? = nil) async throws -> Any {
        var request = URLRequest(url: URL(string: url)!, timeoutInterval: 20)
        request.httpMethod = method
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let form {
            request.httpBody = Data(form.utf8)
            request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        } else if let body {
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        }
        let data: Data, response: URLResponse
        do { (data, response) = try await URLSession.shared.data(for: request) }
        catch { throw ChatError(message: "No connection. Chat needs the internet.") }
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        let json = data.isEmpty ? nil : try? JSONSerialization.jsonObject(with: data)
        if code >= 400 { throw ChatError(message: friendly(json), code: code) }
        return json ?? JSON()
    }

    static func friendly(_ body: Any?) -> String {
        let error = ((body as? JSON) ?? (body as? [JSON])?.first)?["error"] as? JSON
        let msg = error?["message"] as? String ?? ""
        func any(_ codes: String...) -> Bool { codes.contains { msg.hasPrefix($0) } }
        if any("EMAIL_EXISTS") { return "That email already has an account. Sign in instead." }
        if any("INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND") { return "Wrong email or password." }
        if any("WEAK_PASSWORD") { return "Use a password with at least 6 characters." }
        if any("INVALID_EMAIL", "MISSING_EMAIL") { return "Enter a valid email address." }
        if any("MISSING_PASSWORD") { return "Enter your password." }
        if any("TOO_MANY_ATTEMPTS") { return "Too many tries. Wait a few minutes." }
        if any("TOKEN_EXPIRED", "CREDENTIAL_TOO_OLD", "USER_NOT_FOUND", "USER_DISABLED", "INVALID_REFRESH_TOKEN") { return "Please sign in again." }
        if msg.lowercased().contains("permission") { return "Not allowed. Ask a troop leader." }
        return msg.isEmpty ? "Something went wrong. Try again." : msg
    }
}

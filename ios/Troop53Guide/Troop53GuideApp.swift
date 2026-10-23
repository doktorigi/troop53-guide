import AVFoundation
import BackgroundTasks
import SwiftUI
import UserNotifications

@main
struct Troop53GuideApp: App {
    @UIApplicationDelegateAdaptor private var app: AppDelegate
    @StateObject private var store = GuideStore()
    @Environment(\.scenePhase) private var phase
    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(store).tint(TroopTheme.forest).preferredColorScheme(.light)
        }
        .onChange(of: phase) { if $0 == .background { app.stopIntro(); ChatNotifier.schedule() } }
        .backgroundTask(.appRefresh(ChatNotifier.task)) {
            await ChatNotifier.check()
            ChatNotifier.schedule()
        }
    }
}

/// Opens the right group when a chat notification is tapped.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, ObservableObject {
    @Published var openGroup: String?
    private var intro: AVAudioPlayer?
    func stopIntro() { intro?.stop() }
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        // Intro jingle once per cold launch; default ambient session respects the silent switch.
        if let url = Bundle.main.url(forResource: "intro", withExtension: "mp3") {
            intro = try? AVAudioPlayer(contentsOf: url)
            intro?.play()
        }
        return true
    }
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let id = response.notification.request.content.userInfo["group"] as? String
        await MainActor.run { openGroup = id }
    }
}

/// Checks for new troop chat messages in the background and posts a notification, like Android's ChatJob.
/// ponytail: background app refresh polling (iOS decides how often), because instant push needs Cloud Functions (paid Blaze plan).
enum ChatNotifier {
    static let task = "com.doktorigi.troop53.chat"

    static func enable() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
        schedule()
    }

    static func schedule() {
        guard Chat.uid != nil else { return }
        let request = BGAppRefreshTaskRequest(identifier: task)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    static func check() async {
        guard let me = try? await Chat.me(), me.approved, let groups = try? await Chat.groups(me) else { return }
        for g in groups where g.members.contains(me.id) && Chat.isUnread(g) {
            let latest = try? await Chat.messages(g.id, after: nil, latest: 1).last
            Chat.markSeen(g)
            guard let latest, latest.uid != me.id else { continue }
            let content = UNMutableNotificationContent()
            content.title = g.name
            content.body = "\(latest.name): \(latest.text)"
            content.sound = .default
            content.userInfo = ["group": g.id]
            try? await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: g.id, content: content, trigger: nil))
        }
    }
}

import SwiftUI

@main
struct Troop53GuideApp: App {
    @StateObject private var store = GuideStore()
    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(store).tint(TroopTheme.forest).preferredColorScheme(.light)
        }
    }
}

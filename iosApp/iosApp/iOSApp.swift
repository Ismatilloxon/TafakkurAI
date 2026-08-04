import AVFoundation
import SwiftUI

@main
struct iOSApp: App {
    // The audio session is app-global state, so the host app owns it — without a
    // .playback category the ringer switch silences recitation.
    init() {
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

import SwiftUI
import ComposeApp

import SwiftUI
import shared

@main
struct iOSApp: App {
    init() {
        AppLogger.shared.plant()   // Kotlin object의 init()은 Swift에서 예약어라 doInit 등으로 노출될 수 있음
    }
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all, edges: .bottom)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

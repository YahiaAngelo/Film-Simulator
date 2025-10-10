import SwiftUI
import shared

@main
struct iOSApp: App {
    init() {
        HelperKt.doInitKoin()

        // Initialize Metal processor (uncomment after adding Metal files to Xcode project)
         setupMetalProcessor()
         _ = MetalLUTProcessorWrapper.shared
    }
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

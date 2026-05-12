import SwiftUI

@available(macOS 13.0, *)
@main
struct MacTileApp: App {
    
    @AppStorage("showMenuBarExtra") private var showMenuBarExtra = true
    
    @State private var command: String = "a"
           
    var body: some Scene {
        MenuBarExtra("MacTile",
                     systemImage: "star.fill",
                     isInserted: $showMenuBarExtra
        ) {
            ContentView()
        }
        .menuBarExtraStyle(.window)
    }
    
    var body1: some Scene {

        MenuBarExtra(command, systemImage: "\(command).circle") {
           
            Button("Uno") { command = "a" }
                .keyboardShortcut("U")
           
            Button("Dos") { command = "b" }
                .keyboardShortcut("D")
           
            Divider()

            Button("Salir") { NSApplication.shared.terminate(nil) }
                .keyboardShortcut("S")
        }
    }
    
}

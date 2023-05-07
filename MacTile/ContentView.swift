import SwiftUI

struct ContentView: View {
    
    private enum Tabs: Hashable {
        case stream, general, advanced
    }

    var body: some View {
        TabView {
            StreamView()
                .tabItem {
                    Label("Stream", systemImage: "waveform.circle")
                }
                .tag(Tabs.stream)
            GeneralSettingsView()
                .tabItem {
                    Label("General", systemImage: "gear")
                }
                .tag(Tabs.general)
            AdvancedSettingsView()
                .tabItem {
                    Label("Advanced", systemImage: "star")
                }
                .tag(Tabs.advanced)
        }
        .padding(20)
        .frame(width: 375, height: 150)
    }
    
}

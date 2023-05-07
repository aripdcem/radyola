import SwiftUI
import AVFoundation

struct StreamView: View {
    
    @State var song1 = false
    @StateObject private var soundManager = SoundManager()

    var body: some View {
        VStack {
            Image(systemName: song1 ? "pause.circle.fill": "play.circle.fill")
                .font(.system(size: 25))
                .padding(.trailing)
                .onTapGesture {
                    soundManager.playSound(sound: "http://160.75.86.29:8088/listen.pls?sid=3")
                    song1.toggle()
                    
                    if song1{
                        soundManager.audioPlayer?.play()
                    } else {
                        soundManager.audioPlayer?.pause()
                    }
                }
        }
    }
    
}

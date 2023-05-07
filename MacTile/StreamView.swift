import SwiftUI

struct StreamView: View {
    
    @StateObject fileprivate var viewModel = StreamViewModel()
        
    var body: some View {
        List(viewModel.streams) { stream in
            StreamRowView(stream: stream)
        }
        .padding()
        .frame(width: 350, height: 100)
    }
}

private struct StreamRowView: View {

    @State private var song1 = false
    @StateObject private var soundManager = SoundManager()

    var stream: Stream
    
    var body: some View {
        HStack(alignment: .top) {
            Image(systemName: song1 ? "pause.circle.fill": "play.circle.fill")
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(height: 40)
            VStack(alignment: .leading) {
                Text(stream.title)
                    .font(.headline)
                Text("by \(stream.author)")
                    .font(.subheadline)
                Text("\(stream.pages) pages")
                    .font(.subheadline)
            }
            Spacer()
        }
        .onTapGesture {
            soundManager.playSound(sound: stream.url)
            song1.toggle()
            
            if song1 {
                soundManager.audioPlayer?.play()
            } else {
                soundManager.audioPlayer?.pause()
            }
        }
    }
}

private class StreamViewModel: ObservableObject {
    @Published var streams: [Stream] = Stream.samples
}

struct Stream: Identifiable {
    var id = UUID()
    var title: String
    var author: String
    var isbn: String
    var pages: Int
    var url: String
    var isRead: Bool = false
}

extension Stream {
    static let samples = [
        Stream(title: "ITU Radio Jazz/Blues", author: "Matt Gemmell", isbn: "9781916265202", pages: 476, url: "http://160.75.86.29:8088/listen.pls?sid=3"),
        Stream(title: "Açık Radyo", author: "Matt Gemmell", isbn: "9781916265202", pages: 476, url: "https://stream.34bit.net/ar.mp3"),
        Stream(title: "Changer", author: "Matt Gemmell", isbn: "9781916265202", pages: 476, url: "https://"),
        Stream(title: "SwiftUI for Absolute Beginners", author: "Jayant Varma", isbn: "9781484255155", pages: 200, url: "https://"),
        Stream(title: "Why we sleep", author: "Matthew Walker", isbn: "9780141983769", pages: 368, url: "https://"),
        Stream(title: "The Hitchhiker's Guide to the Galaxy", author: "Douglas Adams", isbn: "9780671461492", pages: 216, url: "https://")
    ]
}

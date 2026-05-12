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
    var author: String
    var title: String
    var url: String
}

extension Stream {
    static let samples = [
        Stream(author: "Stream", title: "Açık Radyo", url: "https://stream.34bit.net/ar.mp3"),
        Stream(author: "Stream", title: "Sputnik Türkiye", url: "https://nfw.ria.ru/flv/audio.aspx?ID=98318704&type=mp3"),
        Stream(author: "Stream", title: "ITU Radio Jazz/Blues", url: "http://160.75.86.29:8088/listen.pls?sid=3"),
        Stream(author: "Stream", title: "ITU Radio Classical", url: "http://160.75.86.29:8088/listen.pls?sid=5"),
        Stream(author: "Stream", title: "MUSIQ3", url: "https://redbeemedia.streamabc.net/redbm-musiq3-aac-256-1558698"),
        Stream(author: "Stream", title: "VRT Klara", url: "http://icecast-servers.vrtcdn.be/klara-high.mp3"),
        Stream(author: "Stream", title: "Viva Brabant Wallon", url: "https://radio.rtbf.be/viva-bw/aac-128"),
        Stream(author: "Stream", title: "ITU Radio Rock", url: "http://160.75.86.29:8088/listen.pls?sid=1"),
        Stream(author: "Stream", title: "BBC Radio 1", url: "http://open.live.bbc.co.uk/mediaselector/5/select/version/2.0/mediaset/http-icy-mp3-a/vpid/bbc_radio_one/format/pls.pls"),
        Stream(author: "Stream", title: "BBC World Service News", url: "http://open.live.bbc.co.uk/mediaselector/5/select/mediaset/http-icy-mp3-a/format/pls/proto/http/vpid/bbc_world_service.pls"),
        Stream(author: "Stream", title: "Radyo TRT Haber", url: "https://nmicenotrt.mediatriple.net/trt_haber.aac"),
        Stream(author: "Stream", title: "NTV Radyo", url: "https://dygedge.radyotvonline.net/ntvradyo/playlist.m3u8"),
        Stream(author: "Stream", title: "HABERTÜRK Radyo", url: "https://ciner-live.ercdn.net/haberturkradyo/haberturkradyo_1.m3u8"),
        Stream(author: "Stream", title: "Radio Panik", url: "https://streaming.domainepublic.net/radiopanik.mp3"),
        Stream(author: "Stream", title: "Radyo Bozcaada", url: "http://radyobozcaada.canliyayinda.com:4000/stream"),
        Stream(author: "Stream", title: "Radyo Gökçeada", url: "https://radyogok.80.yayin.com.tr/stream"),
        Stream(author: "Stream", title: "Radyo Boğaziçi", url: "http://nova.radyobogazici.net:7008/listen"),
        Stream(author: "Stream", title: "Μινόρε Καλλονής, Kalloni, Greece", url: "https://i4.streams.ovh:2200/ssl/minore?mp=/stream")
    ]
}

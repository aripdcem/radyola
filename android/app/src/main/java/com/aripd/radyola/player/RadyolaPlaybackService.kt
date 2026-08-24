package com.aripd.radyola.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.os.bundleOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.aripd.radyola.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Ön plan (foreground) servis olarak canlı yayını çalar.
 *
 * MediaSession sayesinde bildirim, kilit ekranı kontrolleri ve kulaklık
 * medya tuşları uygulama arka plandayken de çalışır.
 *
 * Uyku zamanlayıcı da burada yaşar: sayaç ViewModel'de tutulsaydı kullanıcı
 * uygulamayı son kullanılanlardan kaydırdığında ViewModel'le birlikte ölürdü —
 * servis çalmayı sürdürür, radyo sabaha kadar susmaz. Zamanlayıcının tam da
 * önlemesi gereken senaryo bu. UI yalnız görüntüleme sayacı tutar ve servise
 * özel oturum komutlarıyla konuşur.
 */
class RadyolaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private val handler = Handler(Looper.getMainLooper())

    /** Çalmanın duracağı an, [SystemClock.elapsedRealtime] tabanında. 0 = kapalı. */
    private var sleepDeadlineMs = 0L

    /** Kullanıcının seçtiği süre — UI'daki çip vurgusu için geri bildirilir. */
    private var sleepMinutes = 0

    private val sleepRunnable = Runnable {
        mediaSession?.player?.pause()
        sleepDeadlineMs = 0L
        sleepMinutes = 0
    }

    @UnstableApi
    override fun onCreate() {
        super.onCreate()

        // Canlı yayında sunucu yönlendirmeleri (http → https) sık; izin veriyoruz.
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Radyola-Android/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(sessionCallback)
            .build()
    }

    /** Uyku zamanlayıcı komutlarını kabul eden oturum geri çağrısı. */
    @UnstableApi
    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(SessionCommand(CMD_SLEEP_SET, Bundle.EMPTY))
                .add(SessionCommand(CMD_SLEEP_QUERY, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> = when (customCommand.customAction) {
            CMD_SLEEP_SET -> {
                setSleepTimer(args.getInt(KEY_SLEEP_MINUTES, 0))
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, sleepState()))
            }
            CMD_SLEEP_QUERY ->
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, sleepState()))
            else -> super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    /** [minutes] dakika sonra çalmayı durdurur; 0 zamanlayıcıyı iptal eder. */
    private fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(sleepRunnable)
        if (minutes <= 0) {
            sleepDeadlineMs = 0L
            sleepMinutes = 0
            return
        }
        sleepMinutes = minutes
        sleepDeadlineMs = SystemClock.elapsedRealtime() + minutes * 60_000L
        handler.postDelayed(sleepRunnable, minutes * 60_000L)
    }

    /**
     * Zamanlayıcının anlık durumu. Kalan süre mutlak zaman değil süre olarak
     * verilir — istemcinin saati farklı bir tabanda olabilir.
     */
    private fun sleepState(): Bundle = bundleOf(
        KEY_SLEEP_MINUTES to sleepMinutes,
        KEY_SLEEP_REMAINING_SEC to if (sleepDeadlineMs == 0L) 0
        else ((sleepDeadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L) / 1000L).toInt()
    )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Kullanıcı görevi (task) kapattığında: durdurulmuş bir yayın için servisi
     * de kapat, aksi halde arka planda çalmayı sürdür.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.player?.let { player ->
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(sleepRunnable)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val CMD_SLEEP_SET = "com.aripd.radyola.SLEEP_SET"
        const val CMD_SLEEP_QUERY = "com.aripd.radyola.SLEEP_QUERY"
        const val KEY_SLEEP_MINUTES = "minutes"
        const val KEY_SLEEP_REMAINING_SEC = "remainingSec"
    }
}

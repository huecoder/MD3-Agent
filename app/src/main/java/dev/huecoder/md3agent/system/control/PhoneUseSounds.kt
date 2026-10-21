package dev.huecoder.md3agent.system.control

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.media.SoundPool
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.TapSound

class PhoneUseSounds(private val appContext: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val loaded = mapOf(
        TapSound.SOFT_A to pool.load(appContext, R.raw.tap_soft_a, 1),
        TapSound.SOFT_B to pool.load(appContext, R.raw.tap_soft_b, 1),
        TapSound.MOUSE to pool.load(appContext, R.raw.click_mouse, 1),
    )

    fun alert(urgent: Boolean) {
        val type = if (urgent) RingtoneManager.TYPE_RINGTONE else RingtoneManager.TYPE_NOTIFICATION
        val ringtone = RingtoneManager.getRingtone(appContext, RingtoneManager.getDefaultUri(type)) ?: return
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(if (urgent) AudioAttributes.USAGE_NOTIFICATION_RINGTONE else AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone.play()
        mainHandler.postDelayed(ringtone::stop, ALERT_MAX_MS)
    }

    fun play(sound: TapSound, volume: Float) {
        val id = loaded[sound] ?: return
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    private companion object {
        const val MAX_STREAMS = 4
        const val ALERT_MAX_MS = 3_500L
    }
}

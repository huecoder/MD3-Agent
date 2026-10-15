package dev.huecoder.md3agent.system.control

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.TapSound

class PhoneUseSounds(context: Context) {
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
        TapSound.SOFT_A to pool.load(context, R.raw.tap_soft_a, 1),
        TapSound.SOFT_B to pool.load(context, R.raw.tap_soft_b, 1),
        TapSound.MOUSE to pool.load(context, R.raw.click_mouse, 1),
    )

    fun play(sound: TapSound, volume: Float) {
        val id = loaded[sound] ?: return
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    private companion object {
        const val MAX_STREAMS = 4
    }
}

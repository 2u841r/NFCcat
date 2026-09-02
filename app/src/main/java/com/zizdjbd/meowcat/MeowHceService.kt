package com.zizdjbd.meowcat

import android.media.AudioAttributes
import android.media.SoundPool
import android.nfc.cardemulation.HostApduService
import android.os.Bundle

class MeowHceService : HostApduService() {

    private lateinit var soundPool: SoundPool
    private var meowId = 0

    @Volatile private var loaded = false
    @Volatile private var playWhenLoaded = false

    override fun onCreate() {
        super.onCreate()
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        soundPool.setOnLoadCompleteListener { _, _, status ->
            loaded = status == 0
            if (loaded && playWhenLoaded) {
                playWhenLoaded = false
                meow()
            }
        }
        meowId = soundPool.load(this, R.raw.meow, 1)
    }

    override fun processCommandApdu(apdu: ByteArray?, extras: Bundle?): ByteArray {
        // Loading is async; the SELECT can beat it. Queue the play if so.
        if (loaded) meow() else playWhenLoaded = true
        return SW_OK
    }

    private fun meow() {
        soundPool.play(meowId, 1f, 1f, 1, 0, 1f)
    }

    override fun onDeactivated(reason: Int) { }

    override fun onDestroy() {
        soundPool.release()
        super.onDestroy()
    }

    companion object {
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
    }
}

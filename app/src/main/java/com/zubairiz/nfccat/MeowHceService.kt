package com.zubairiz.nfccat

import android.media.AudioManager
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

class MeowHceService : HostApduService() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: service starting")
        // Cheap: reads the raw PCM, no decoder, so it is ready before the SELECT lands.
        MeowPlayer.prepare(applicationContext)
    }

    override fun processCommandApdu(apdu: ByteArray?, extras: Bundle?): ByteArray {
        Log.d(TAG, "processCommandApdu: apdu=${apdu?.toHex()}")
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        Log.d(
            TAG,
            "volume=${am.getStreamVolume(AudioManager.STREAM_MUSIC)}/" +
                "${am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)} ringer=${am.ringerMode}"
        )
        // Anything routed here has already matched our AID, so just meow.
        MeowPlayer.play(applicationContext)
        return SW_OK
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "onDeactivated: reason=$reason")
    }

    override fun onDestroy() {
        // Deliberately does NOT stop playback. Android destroys this service ~30ms after the
        // tap, while the meow is still sounding; the player is process-scoped and outlives it.
        Log.d(TAG, "onDestroy: service gone, playback continues")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MeowHce"
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)

        private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
    }
}

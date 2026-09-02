package com.zizdjbd.meowcat

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Plays the meow without going anywhere near a decoder.
 *
 * The HCE service lives about 30ms: Android binds it when the field arrives and destroys it as
 * soon as the field drops. That is far too short for SoundPool/MediaPlayer, whose loads are
 * asynchronous and never complete before onDestroy. res/raw/meow.wav is already uncompressed
 * PCM, so we parse the header ourselves and hand the samples straight to AudioTrack, which
 * starts synchronously.
 *
 * State is process-scoped rather than service-scoped, so it survives the service being torn
 * down and the next tap reuses the already-read samples.
 */
object MeowPlayer {

    private const val TAG = "MeowHce"

    private var pcm: ByteArray? = null
    private var sampleRate = 44100
    private var channels = 2
    private var track: AudioTrack? = null

    /** Reads and caches the PCM. Cheap enough to call on the main thread, no decoding involved. */
    @Synchronized
    fun prepare(context: Context) {
        if (pcm != null) return
        val raw = context.resources.openRawResource(R.raw.meow).use { it.readBytes() }
        parseWav(raw)
        Log.d(TAG, "MeowPlayer.prepare: pcm=${pcm?.size} bytes rate=$sampleRate ch=$channels")
    }

    @Synchronized
    fun play(context: Context) {
        prepare(context)
        val samples = pcm ?: run {
            Log.w(TAG, "MeowPlayer.play: no PCM, nothing to play")
            return
        }

        // Release any previous one-shot track before starting another.
        track?.runCatching { stop(); release() }
        track = null

        val channelMask =
            if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO

        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setBufferSizeInBytes(samples.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        val written = t.write(samples, 0, samples.size)
        t.play()
        track = t
        Log.d(TAG, "MeowPlayer.play: wrote=$written state=${t.state} playState=${t.playState}")
    }

    /** Minimal RIFF/WAVE walk: find fmt for the format and data for the samples. */
    private fun parseWav(raw: ByteArray) {
        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        if (raw.size < 12 || String(raw, 0, 4) != "RIFF" || String(raw, 8, 4) != "WAVE") {
            Log.w(TAG, "MeowPlayer: not a RIFF/WAVE file")
            return
        }
        var pos = 12
        while (pos + 8 <= raw.size) {
            val id = String(raw, pos, 4)
            val size = bb.getInt(pos + 4)
            val body = pos + 8
            if (size < 0 || body + size > raw.size) break
            when (id) {
                "fmt " -> {
                    channels = bb.getShort(body + 2).toInt()
                    sampleRate = bb.getInt(body + 4)
                }
                "data" -> {
                    pcm = raw.copyOfRange(body, body + size)
                    return
                }
            }
            pos = body + size + (size and 1) // chunks are word aligned
        }
        Log.w(TAG, "MeowPlayer: no data chunk found")
    }
}

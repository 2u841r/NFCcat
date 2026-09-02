package com.zubairiz.nfccat

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException

class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var statusText: TextView
    private lateinit var meowButton: Button

    @Volatile private var armed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        statusText = findViewById(R.id.statusText)
        meowButton = findViewById(R.id.meowButton)

        meowButton.setOnClickListener {
            when {
                nfcAdapter == null -> Unit
                nfcAdapter?.isEnabled == false ->
                    startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                else -> {
                    armed = true
                    statusText.text = getString(R.string.status_armed)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        armed = false
        refreshNfcState()
    }

    /** Re-checks NFC every time we come back, so returning from settings updates the UI. */
    private fun refreshNfcState() {
        val adapter = nfcAdapter
        when {
            adapter == null -> {
                statusText.text = getString(R.string.status_no_nfc)
                meowButton.isEnabled = false
                meowButton.text = getString(R.string.button_meow)
            }
            !adapter.isEnabled -> {
                statusText.text = getString(R.string.status_nfc_off)
                meowButton.isEnabled = true
                meowButton.text = getString(R.string.button_enable_nfc)
            }
            else -> {
                statusText.text = getString(R.string.status_ready)
                meowButton.isEnabled = true
                meowButton.text = getString(R.string.button_meow)
                adapter.enableReaderMode(
                    this, this,
                    NfcAdapter.FLAG_READER_NFC_A or
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                        NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    null
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag) {
        if (!armed) return
        armed = false

        val isoDep = IsoDep.get(tag) ?: return
        var ok = false
        try {
            isoDep.connect()
            isoDep.timeout = 2000
            val response = isoDep.transceive(SELECT_APDU)
            ok = response.size >= 2 &&
                response[response.size - 2] == 0x90.toByte() &&
                response[response.size - 1] == 0x00.toByte()
        } catch (e: IOException) {
            // moved apart too soon
        } finally {
            try { isoDep.close() } catch (_: IOException) {}
        }
        runOnUiThread {
            statusText.text = getString(if (ok) R.string.status_sent else R.string.status_no_cat)
        }
    }

    companion object {
        private const val AID = "F0010203040506"
        private val SELECT_APDU = buildSelectApdu(AID)

        private fun buildSelectApdu(aid: String): ByteArray {
            val aidBytes = hexToBytes(aid)
            return byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aidBytes.size.toByte()) +
                aidBytes +
                byteArrayOf(0x00)
        }

        private fun hexToBytes(hex: String): ByteArray =
            ByteArray(hex.length / 2) {
                hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
            }
    }
}

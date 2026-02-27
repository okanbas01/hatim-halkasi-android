package com.example.sharedkhatm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer

class RadioPlayerFragment : Fragment(R.layout.fragment_radio_player) {

    private var player: ExoPlayer? = null
    private var isPlaying = false

    companion object {
        private const val TAG = "RadioPlayerFragment"
    }

    // Genel Kur'an Radyo
    private val RADIO_URL = "https://radio.mp3islam.com/listen/quran_radio/radio.mp3"

    private lateinit var btnPlayStop: FloatingActionButton
    private lateinit var txtInfo: TextView
    private lateinit var txtStatus: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<ImageView>(R.id.btnBack)
        btnPlayStop = view.findViewById(R.id.btnPlayStop)
        txtInfo = view.findViewById(R.id.txtRadioInfo)
        txtStatus = view.findViewById(R.id.txtRadioStatus)
        val txtCredit = view.findViewById<TextView>(R.id.txtCredit)

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        // Kaynak Linkine Tıklama İşlemi
        txtCredit.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mp3islam.com"))
                startActivity(intent)
            } catch (e: Exception) {
                // Tarayıcı bulunamazsa sessiz kal
            }
        }

        btnPlayStop.setImageResource(R.drawable.ic_media_play)

        btnPlayStop.setOnClickListener {
            if (isPlaying) stopRadio() else startRadio()
        }
    }

    private fun startRadio() {
        txtInfo.text = "Kur'an Tilaveti – Canlı"
        txtStatus.text = "Bağlanıyor..."
        btnPlayStop.isEnabled = false

        if (player == null) initPlayer()

        player?.apply {
            setMediaItem(MediaItem.fromUri(RADIO_URL))
            prepare()
            playWhenReady = true
            play()
        }

        isPlaying = true
        btnPlayStop.setImageResource(R.drawable.ic_radio_stop)
    }

    private fun initPlayer() {
        player = ExoPlayer.Builder(requireContext()).build().also { exo ->

            exo.addListener(object : Player.Listener {

                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            txtStatus.text = "Yükleniyor..."
                            btnPlayStop.isEnabled = false
                        }
                        Player.STATE_READY -> {
                            txtStatus.text = "Şu an yayında 📡"
                            btnPlayStop.isEnabled = true
                        }
                        Player.STATE_ENDED -> {
                            txtStatus.text = "Yayın sona erdi"
                            updateStoppedUI()
                        }
                        else -> Unit
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "ExoPlayer error", error)

                    val httpCode = extractHttpStatus(error)
                    val msg = if (httpCode != null) {
                        "Bağlantı hatası (HTTP $httpCode)"
                    } else {
                        "Oynatma hatası (kod: ${error.errorCode})"
                    }

                    txtStatus.text = msg
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    updateStoppedUI()
                }
            })
        }
    }

    private fun extractHttpStatus(error: PlaybackException): Int? {
        val cause = error.cause
        return if (cause is HttpDataSource.InvalidResponseCodeException) {
            cause.responseCode
        } else {
            null
        }
    }

    private fun stopRadio() {
        player?.apply {
            stop()
            clearMediaItems()
        }
        updateStoppedUI()
    }

    private fun updateStoppedUI() {
        isPlaying = false
        btnPlayStop.isEnabled = true
        btnPlayStop.setImageResource(R.drawable.ic_media_play)
        if (txtStatus.text.isNullOrBlank()) {
            txtStatus.text = "Başlatmak için dokunun"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
    }
}
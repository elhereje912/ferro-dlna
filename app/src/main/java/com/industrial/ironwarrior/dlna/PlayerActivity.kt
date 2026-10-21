package com.industrial.ironwarrior.dlna

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PlayerActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var surfaceView: SurfaceView
    private lateinit var btnPlayPause: Button
    private lateinit var seekBar: SeekBar
    private lateinit var txtTitle: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val updateProgressTask = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    seekBar.progress = it.currentPosition
                    handler.postDelayed(this, 500) // Tasa de actualización ligera (500ms)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        surfaceView = findViewById(R.id.surfaceView)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        seekBar = findViewById(R.id.seekBar)
        txtTitle = findViewById(R.id.txtTitle)

        txtTitle.text = intent.getStringExtra("DEVICE_NAME") ?: "REPRODUCTOR FERRO"
        surfaceView.holder.addCallback(this)

        btnPlayPause.setOnClickListener {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    btnPlayPause.text = "PLAY"
                    handler.removeCallbacks(updateProgressTask)
                } else {
                    it.start()
                    btnPlayPause.text = "PAUSA"
                    handler.post(updateProgressTask)
                }
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) mediaPlayer?.seekTo(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val streamUrl = intent.getStringExtra("MEDIA_URL") ?: return

        // Instanciación directa con SurfaceView (0-copy directo a SurfaceFlinger)
        mediaPlayer = MediaPlayer().apply {
            setDisplay(holder)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(this@PlayerActivity, Uri.parse(streamUrl))
            setOnPreparedListener { mp ->
                seekBar.max = mp.duration
                mp.start()
                btnPlayPause.text = "PAUSA"
                handler.post(updateProgressTask)
            }
            prepareAsync()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        handler.removeCallbacks(updateProgressTask)
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateProgressTask)
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

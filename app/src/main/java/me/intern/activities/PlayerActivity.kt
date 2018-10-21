package me.intern.activities

import android.media.MediaPlayer
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.MediaController
import kotlinx.android.synthetic.main.activity_player.*
import me.intern.R
import me.intern.services.VideoDownloadPlayHandler

class PlayerActivity : AppCompatActivity() {

    private val tag = "suthar-player"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val videoUrl = intent.getStringExtra("videoUrl")

        streamVideo(videoUrl)

    }

    private fun streamVideo(videoUrl: String) {

        VideoDownloadPlayHandler().startServer(this, videoUrl, object : VideoDownloadPlayHandler.VideoStreamInterface {
            override fun onServerStart(videoStreamUrl: String) {
                runOnUiThread {
                    playVideo(videoStreamUrl)
                }
            }
        })
    }

    private fun playVideo(videoStreamUrl: String) {

        Log.d(tag, "Video Url: $videoStreamUrl")

        videoView.setVideoPath(videoStreamUrl)
        videoView.setOnPreparedListener {
            it.start()
            progress.visibility = View.INVISIBLE

            it.setOnBufferingUpdateListener { mp, percent ->
                Log.d(tag, "Buffer Percentage: $percent")
            }
        }

        videoView.setOnErrorListener { mp, what, extra ->
            Log.e(tag, "setOnErrorListener $what, extra: $extra")
            if (extra == MediaPlayer.MEDIA_ERROR_SERVER_DIED || extra == MediaPlayer.MEDIA_ERROR_MALFORMED) {
                Log.d(tag, "erroronplaying")
            } else if (extra == MediaPlayer.MEDIA_ERROR_IO) {
                Log.d(tag, "erroronplaying MEDIA_ERROR_IO")
            }
            false
        }

        videoView.setOnCompletionListener {
            Log.d(tag, "Video Completed")
            finish()
        }

        val controller = MediaController(this@PlayerActivity, true)
        videoView.setMediaController(controller)
        videoView.start()
    }
}
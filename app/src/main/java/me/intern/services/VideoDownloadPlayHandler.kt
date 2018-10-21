package me.intern.services

import android.app.Activity
import android.os.Environment
import android.util.Log
import org.jetbrains.anko.doAsync
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class VideoDownloadPlayHandler {

    private val tag = "suthar"
    private var server: LocalServer? = null

    fun startServer(activity: Activity, remoteUrl: String, callback: VideoStreamInterface) {

        val file = calculateFile(remoteUrl)

        shouldDownload(remoteUrl, file, object : VideoDownloadPlayHandler.ShouldDownloadInterface {
            override fun onComplete(shouldDownload: Boolean) {
                if (shouldDownload) {
                    VideoDownloader(activity, remoteUrl, file)
                    val server = LocalServer(file.path)
                    this@VideoDownloadPlayHandler.server = server

                    Thread(Runnable {
                        server.init()

                        activity.runOnUiThread {
                            server.start()
                            callback.onServerStart(server.getFileUrl())
                        }
                    }).start()
                } else {
                    callback.onServerStart(file.absolutePath)
                }
            }

        })

    }

    private fun calculateFile(downloadUrl: String): File {
        val videoName = downloadUrl.substring(downloadUrl.lastIndexOf("/"))
        val dir = File(Environment.getExternalStorageDirectory().toString() + "/" + Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdir()
        return File(dir, videoName)
    }

    private fun shouldDownload(path: String, file: File, callback: ShouldDownloadInterface) {

        doAsync {
            val url = URL(path)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(tag, "HTTP " + connection.responseCode + " " + connection.responseMessage)
                callback.onComplete(true)
                return@doAsync
            }

            val fileLength = connection.contentLength.toLong()

            Log.d(tag, "LocalFileLength: ${file.length()}, RemoteFileLength: $fileLength")

            if (!file.exists()) {
                Log.d(tag, "Stream Video")
                file.createNewFile()
                callback.onComplete(true)
            } else if (file.length() != fileLength) {
                Log.d(tag, "Stream Video")
                file.delete()
                file.createNewFile()
                callback.onComplete(true)
            } else {
                Log.d(tag, "Local Video")
                callback.onComplete(false)
            }
        }
    }

    interface ShouldDownloadInterface {
        fun onComplete(shouldDownload: Boolean)
    }

    fun start() {
        server?.start()
    }

    fun stop() {
        server?.stop()
    }

    interface VideoStreamInterface {
        fun onServerStart(videoStreamUrl: String)
    }
}
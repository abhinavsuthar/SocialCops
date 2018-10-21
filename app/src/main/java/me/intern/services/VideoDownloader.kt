package me.intern.services

import android.content.Context
import android.util.Log
import org.jetbrains.anko.doAsync
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class VideoDownloader(ctx: Context, downloadUrl: String, fileToSave: File) {

    init {
        doAsync {
            downloadVideo(downloadUrl, fileToSave)
        }
    }

    private val tag = "suthar"

    companion object {
        const val DATA_READY = 1
        const val DATA_NOT_READY = 2
        const val DATA_CONSUMED = 3
        const val DATA_NOT_AVAILABLE = 4

        var dataStatus = -1
        var consumedb = 0L
        private var currentBytes = 0L
        private var fileLength = -1L

        fun isDataReady(): Boolean {
            dataStatus = -1
            var res = false
            when {
                fileLength == currentBytes -> dataStatus = DATA_CONSUMED
                currentBytes <= consumedb -> dataStatus = DATA_NOT_READY
                fileLength == -1L -> dataStatus = DATA_NOT_AVAILABLE

                currentBytes > consumedb -> {
                    dataStatus = DATA_READY
                    res = true
                }
            }
            Log.d("suthar", "FileLength: $fileLength, ReadB: $currentBytes, ConsumedB: $consumedb")
            return res
        }
    }

    private fun downloadVideo(path: String, file: File) {

        val url = URL(path)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            Log.e(tag, "HTTP " + connection.responseCode + " " + connection.responseMessage)
            return
        }

        fileLength = connection.contentLength.toLong()

        if (!shouldDownload(file)) {
            currentBytes = fileLength
            return
        }


        val fos = FileOutputStream(file)
        val inputStream = connection.inputStream

        val buffer = ByteArray(1024)
        var bytesRead = inputStream.read(buffer)
        while (bytesRead >0) {
            fos.write(buffer, 0, bytesRead)
            bytesRead = inputStream.read(buffer)
            currentBytes += bytesRead
            //Log.d(tag, "" + currentBytes / 1024 + "kb of " + fileLength / 1024 + "kb")
        }

        fos.close()
        inputStream.close()
    }

    private fun shouldDownload(file: File): Boolean {

        Log.d(tag, "LocalFileLength: ${file.length()}, RemoteFileLength: $fileLength")

        return if (!file.exists()) {
            Log.d(tag, "Stream Video")
            file.createNewFile()
            true
        } else if (file.length() != fileLength) {
            Log.d(tag, "Stream Video")
            file.delete()
            file.createNewFile()
            true
        } else {
            Log.d(tag, "Local Video")
            false
        }

    }
}
package me.intern.services

import android.util.Log
import java.io.*
import java.net.*
import java.util.*


class LocalServer(private val path: String) : Runnable {


    private val tag = "suthar"
    private var thread: Thread? = null
    private var isRunning = false
    private lateinit var serverSocket: ServerSocket
    private val file: File by lazy { File(path) }
    private var port = 0

    private var cbSkip = 0L
    private var seekRequest = false


    fun init(): String {

        val ip = "localhost"
        val inet = InetAddress.getByName(ip)
        val bytes = inet.address
        val socket = ServerSocket(port, 0, InetAddress.getByAddress(bytes))
        this.serverSocket = socket

        socket.soTimeout = 10000
        port = socket.localPort
        val serverUrl = "http://" + socket.inetAddress.hostAddress + ":" + port
        Log.e(tag, "LocalServer started at $serverUrl")

        return serverUrl
    }

    fun getFileUrl() = "http://" + serverSocket.inetAddress.hostAddress + ":" + port + "/" + file.name

    fun start() {
        thread = Thread(this)
        thread?.start()
        isRunning = true
    }

    fun stop() {
        isRunning = false
        if (thread == null) {
            Log.e(tag, "LocalServer was stopped without being started.")
            return
        }
        Log.e(tag, "Stopping server.")
        thread?.interrupt()
    }

    fun isRunning() = isRunning

    override fun run() {
        while (isRunning) {
            try {
                val client = serverSocket.accept() ?: continue
                Log.e(tag, "client connected at $port")
                Log.e(tag, "processing request...")
                processRequest(ExternalResourceDataSource(file), client)
            } catch (e: Exception) {
                Log.e(tag, "Error connecting to client", e)
            } catch (e: SocketTimeoutException) {
                Log.e(tag, "No client connected, waiting for client...", e)
            }
        }
    }

    private fun processRequest(dataSource: ExternalResourceDataSource, client: Socket) {


        val inputStream = client.getInputStream()
        val bufsize = 8192
        val buf = ByteArray(bufsize)
        var splitbyte: Int
        var rlen = 0

        var read = inputStream.read(buf, 0, bufsize)
        while (read > 0) {
            rlen += read
            splitbyte = findHeaderEnd(buf, rlen)
            if (splitbyte > 0) break
            read = inputStream.read(buf, rlen, bufsize - rlen)
        }


        // Create a BufferedReader for parsing the header.
        val hbis = ByteArrayInputStream(buf, 0, rlen)
        val hin = BufferedReader(InputStreamReader(hbis))
        val pre = Properties()
        val parms = Properties()
        val header = Properties()

        try {
            decodeHeader(hin, pre, parms, header)
        } catch (e1: InterruptedException) {
            Log.e(tag, "Exception: " + e1.message)
            e1.printStackTrace()
        }

        for ((key, value) in header) Log.e(tag, "Header: $key : $value")


        var range: String? = header.getProperty("range")
        cbSkip = 0
        seekRequest = false
        if (range != null) {
            Log.e(tag, "range is: $range")
            seekRequest = true
            range = range.substring(6)
            val charPos = range.indexOf('-')
            if (charPos > 0) {
                range = range.substring(0, charPos)
            }
            cbSkip = range.toLong()
            Log.e(tag, "range found!! $cbSkip")
        }


        var headers = ""
        // Log.e(TAG, "is seek request: " + seekRequest);
        if (seekRequest) {// It is a seek or skip request if there's a Range
            // header
            headers += "HTTP/1.1 206 Partial Content\r\n"
            headers += "Content-Type: " + dataSource.getContentType() + "\r\n"
            headers += "Accept-Ranges: bytes\r\n"
            headers += ("Content-Length: " + dataSource.getContentLength(false)
                    + "\r\n")
            headers += ("Content-Range: bytes " + cbSkip + "-"
                    + dataSource.getContentLength(true) + "/*\r\n")
            headers += "\r\n"
        } else {
            headers += "HTTP/1.1 200 OK\r\n"
            headers += "Content-Type: " + dataSource.getContentType() + "\r\n"
            headers += "Accept-Ranges: bytes\r\n"
            headers += ("Content-Length: " + dataSource.getContentLength(false)
                    + "\r\n")
            headers += "\r\n"
        }

        var data: InputStream? = null
        try {
            data = dataSource.createInputStream()
            val buffer = headers.toByteArray()
            Log.e(tag, "writing to client")
            client.getOutputStream().write(buffer, 0, buffer.size)

            // Start sending content.

            val buff = ByteArray(1024 * 50)
            Log.e(tag, "No of bytes skipped: " + data.skip(cbSkip))
            var cbSentThisBatch = 0
            while (isRunning) {

                while (!VideoDownloader.isDataReady()) {
                    if (VideoDownloader.dataStatus == VideoDownloader.DATA_READY) {
                        Log.d(tag, "**********(Data ready)")
                        break
                    } else if (VideoDownloader.dataStatus == VideoDownloader.DATA_CONSUMED) {
                        Log.d(tag, "error in reading bytess**********(All Data consumed)")
                        break
                    } else if (VideoDownloader.dataStatus == VideoDownloader.DATA_NOT_READY) {
                        Log.e(tag, "error in reading bytess**********(Data not ready)")
                    } else if (VideoDownloader.dataStatus == VideoDownloader.DATA_NOT_AVAILABLE) {
                        Log.e(tag, "error in reading bytess**********(Data not available)")
                    }
                    // wait for a second if data is not ready
                    synchronized(this) {
                        Thread.sleep(1000)
                    }
                }
                Log.e(tag, "No error in reading bytess**********(Data ready)")

                var cbRead = data!!.read(buff, 0, buff.size)
                if (cbRead == -1) {
                    Log.e(tag, "readybytes are -1 and this is simulate streaming, close the ips and create another  ")
                    data.close()
                    data = dataSource.createInputStream()
                    cbRead = data.read(buff, 0, buff.size)
                    if (cbRead == -1) {
                        Log.e(tag, "error in reading bytess**********")
                        throw IOException(
                                "Error re-opening data source for looping.")
                    }
                }
                client.getOutputStream().write(buff, 0, cbRead)
                client.getOutputStream().flush()
                cbSkip += cbRead.toLong()
                cbSentThisBatch += cbRead

                VideoDownloader.consumedb += cbRead
            }
            Log.e(tag, "cbSentThisBatch: $cbSentThisBatch")
            // If we did nothing this batch, block for a second
            if (cbSentThisBatch == 0) {
                Log.e(tag, "Blocking until more data appears")
                Thread.sleep(1000)
            }
        } catch (e: SocketException) {
            // Ignore when the client breaks connection
            Log.e(tag, "Ignoring " + e.message)
        } catch (e: IOException) {
            Log.e(tag, "Error getting content stream.", e)
        } catch (e: Exception) {
            Log.e(tag, "Error streaming file content.", e)
        } finally {
            data?.close()
            client.close()
        }

    }

    private fun findHeaderEnd(buf: ByteArray, rlen: Int): Int {
        var splitbyte = 0
        while (splitbyte + 3 < rlen) {
            if (buf[splitbyte] == '\r'.toByte() && buf[splitbyte + 1] == '\n'.toByte()
                    && buf[splitbyte + 2] == '\r'.toByte() && buf[splitbyte + 3] == '\n'.toByte())
                return splitbyte + 4
            splitbyte++
        }
        return 0
    }

    @Throws(InterruptedException::class)
    private fun decodeHeader(bufferedReader: BufferedReader, pre: Properties, parms: Properties, header: Properties) {
        try {
            // Read the request line
            val inLine = bufferedReader.readLine() ?: return
            val st = StringTokenizer(inLine)
            if (!st.hasMoreTokens()) Log.e(tag, "BAD REQUEST: Syntax error. Usage: GET /example/file.html")

            val method = st.nextToken()
            pre["method"] = method

            if (!st.hasMoreTokens()) Log.e(tag, "BAD REQUEST: Missing URI. Usage: GET /example/file.html")

            var uri: String? = st.nextToken()

            // Decode parameters from the URI
            val qmi = uri?.indexOf('?') ?: return
            if (qmi >= 0) {
                decodeParms(uri.substring(qmi + 1), parms)
                uri = decodePercent(uri.substring(0, qmi))
            } else uri = decodePercent(uri)

            if (st.hasMoreTokens()) {
                var line: String? = bufferedReader.readLine()
                while (line != null && line.trim { it <= ' ' }.isNotEmpty()) {
                    val p = line.indexOf(':')
                    if (p >= 0) header[line.substring(0, p).trim { it <= ' ' }.toLowerCase()] = line.substring(p + 1).trim { it <= ' ' }
                    line = bufferedReader.readLine()
                }
            }

            pre["uri"] = uri
        } catch (ioe: IOException) {
            Log.e(tag, "SERVER INTERNAL ERROR: IOException: " + ioe.message)
        }

    }

    @Throws(InterruptedException::class)
    private fun decodeParms(parms: String?, p: Properties) {
        if (parms == null)
            return

        val st = StringTokenizer(parms, "&")
        while (st.hasMoreTokens()) {
            val e = st.nextToken()
            val sep = e.indexOf('=')
            if (sep >= 0)
                p[decodePercent(e.substring(0, sep))!!.trim { it <= ' ' }] = decodePercent(e.substring(sep + 1))
        }
    }

    @Throws(InterruptedException::class)
    private fun decodePercent(str: String): String? {
        try {
            val sb = StringBuffer()
            var i = 0
            while (i < str.length) {
                val c = str[i]
                when (c) {
                    '+' -> sb.append(' ')
                    '%' -> {
                        sb.append(Integer.parseInt(str.substring(i + 1, i + 3), 16).toChar())
                        i += 2
                    }
                    else -> sb.append(c)
                }
                i++
            }
            return sb.toString()
        } catch (e: Exception) {
            Log.e(tag, "BAD REQUEST: Bad percent-encoding.")
            return null
        }
    }

    inner class ExternalResourceDataSource(private val file: File) {

        fun getContentType(): String {
            // TODO: Support other media if we need to
            return "video/*"
        }

        @Throws(IOException::class)
        fun createInputStream(): InputStream {
            // NB: Because createInputStream can only be called once per asset
            // we always create a new file descriptor here.
            return getInputStream()
        }

        fun getContentLength(ignoreSimulation: Boolean): Long {
            return if (!ignoreSimulation) -1
            else file.length()
        }

        private fun getInputStream(): FileInputStream {
            val inputStream = FileInputStream(file)
            val contentLength = file.length()
            Log.e(tag, "file exists??" + file.exists() + " and content length is: " + contentLength)

            return inputStream
        }
    }
}

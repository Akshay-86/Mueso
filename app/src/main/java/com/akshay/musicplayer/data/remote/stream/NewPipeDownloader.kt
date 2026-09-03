package com.akshay.musicplayer.data.remote.stream

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException

class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {
    companion object {
        private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        private const val DEFAULT_COOKIE = "PREF=hl=en&gl=US; SOCS=CAI"
    }

    @Throws(IOException::class)
    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val reqBuilder = Request.Builder().url(url)
        var hasUserAgent = false
        var hasCookie = false
        var hasAcceptLang = false

        headers.forEach { (name, values) ->
            if (name.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
            if (name.equals("Cookie", ignoreCase = true)) hasCookie = true
            if (name.equals("Accept-Language", ignoreCase = true)) hasAcceptLang = true
            values.forEach { v -> reqBuilder.addHeader(name, v) }
        }

        if (!hasUserAgent) {
            reqBuilder.header("User-Agent", DEFAULT_USER_AGENT)
        }
        if (!hasCookie) {
            reqBuilder.header("Cookie", DEFAULT_COOKIE)
        }
        if (!hasAcceptLang) {
            reqBuilder.header("Accept-Language", "en-US,en;q=0.9")
        }

        val body = if (dataToSend != null) dataToSend.toRequestBody(null) else null
        when (httpMethod) {
            "GET" -> reqBuilder.get()
            "POST" -> reqBuilder.post(body ?: "".toRequestBody(null))
            "HEAD" -> reqBuilder.head()
            else -> reqBuilder.method(httpMethod, body)
        }

        val call = client.newCall(reqBuilder.build())
        val response = call.execute()

        val responseBody = response.body?.string() ?: ""
        val responseHeaders = mutableMapOf<String, List<String>>()
        response.headers.names().forEach { name ->
            responseHeaders[name] = response.headers(name)
        }

        return Response(
            response.code,
            response.message,
            responseHeaders,
            responseBody,
            response.request.url.toString()
        )
    }
}

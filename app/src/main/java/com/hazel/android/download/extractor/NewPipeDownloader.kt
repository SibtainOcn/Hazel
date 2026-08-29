package com.hazel.android.download.extractor

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/**
 * Carries the extractor's HTTP traffic over OkHttp, which the app already ships.
 *
 * The extractor library defines the calls it needs to make and leaves the transport to the
 * host application, so this is the whole of the integration: translate its request into an
 * OkHttp one, and its response back.
 */
class NewPipeDownloader(builder: OkHttpClient.Builder = OkHttpClient.Builder()) : Downloader() {

    private val client: OkHttpClient = builder
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()

        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(request.url())
            // A desktop browser agent. Sites hand a mobile agent a different page, whose
            // shape the extractor is not written against.
            .header("User-Agent", USER_AGENT)

        request.headers().forEach { (name, values) ->
            when {
                values.size > 1 -> {
                    builder.removeHeader(name)
                    values.forEach { builder.addHeader(name, it) }
                }
                values.size == 1 -> builder.header(name, values[0])
            }
        }

        val response = client.newCall(builder.build()).execute()

        // A challenge is not something the app can answer, and it is not a failure worth
        // reporting either: the caller falls back to yt-dlp, which asks a different way.
        if (response.code == HTTP_TOO_MANY_REQUESTS) {
            response.close()
            throw ReCaptchaException("Rate limited", request.url())
        }

        return response.use {
            Response(
                it.code,
                it.message,
                it.headers.toMultimap(),
                it.body.string(),
                it.request.url.toString()
            )
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val READ_TIMEOUT_SECONDS = 20L
        const val HTTP_TOO_MANY_REQUESTS = 429

        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}

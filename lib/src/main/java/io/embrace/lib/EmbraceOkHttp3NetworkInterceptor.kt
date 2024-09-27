package io.embrace.lib

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http.RealResponseBody
import okhttp3.internal.http.promisesBody
import okio.Buffer
import okio.GzipSource
import okio.buffer
import java.io.IOException

/**
 * Custom OkHttp Interceptor implementation that will log the results of the network call
 * to Embrace.io.
 *
 * This interceptor will only intercept network request and responses from client app.
 * OkHttp network interceptors are added almost at the end of stack, they are closer to "Wire"
 * so they are able to see catch "real requests".
 *
 * Network Interceptors
 * - Able to operate on intermediate responses like redirects and retries.
 * - Not invoked for cached responses that short-circuit the network.
 * - Observe the data just as it will be transmitted over the network.
 * - Access to the Connection that carries the request.
 */
class EmbraceOkHttp3NetworkInterceptor internal constructor(
    private val embrace: EmbraceAbrastraction
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        // If the SDK has not started, don't do anything
        val originalRequest: Request = chain.request()
        if (!embrace.isStarted()) {
            return chain.proceed(originalRequest)
        }


        var traceparent: String? = null
        if (originalRequest.header(TRACEPARENT_HEADER_NAME) == null) {
            traceparent = embrace.generateW3cTraceparent()
        }
        val request =
            if (traceparent == null) originalRequest else originalRequest.newBuilder().header(
                TRACEPARENT_HEADER_NAME, traceparent).build()

        // Take a snapshot of the difference in the system and SDK clocks and send the request along the chain
        val networkResponse: Response = chain.proceed(request)

        // Get response and determine the size of the body
        var contentLength: Long? = getContentLengthFromHeader(networkResponse)

        if (contentLength == null) {
            // If we get the body for a server-sent events stream, then we will wait forever
            contentLength = getContentLengthFromBody(networkResponse, networkResponse.header(
                CONTENT_TYPE_HEADER_NAME, null))
        }

        if (contentLength == null) {
            // Set the content length to 0 if we can't determine it
            contentLength = 0L
        }

        var response: Response = networkResponse
        var networkCaptureData: OkhttpCapturedData? = null
        val shouldCaptureNetworkData = embrace.shouldCaptureNetworkBody(request.url.toString(), request.method)

        // If we need to capture the network response body,
        if (shouldCaptureNetworkData) {
            if (ENCODING_GZIP.equals(networkResponse.header(CONTENT_ENCODING_HEADER_NAME, null), ignoreCase = true) &&
                networkResponse.promisesBody()
            ) {
                val body = networkResponse.body
                if (body != null) {
                    val strippedHeaders = networkResponse.headers.newBuilder()
                        .removeAll(CONTENT_ENCODING_HEADER_NAME)
                        .removeAll(CONTENT_LENGTH_HEADER_NAME)
                        .build()
                    val realResponseBody = RealResponseBody(
                        networkResponse.header(CONTENT_TYPE_HEADER_NAME, null),
                        -1L,
                        GzipSource(body.source()).buffer()
                    )
                    val responseBuilder = networkResponse.newBuilder().request(request)
                    responseBuilder.headers(strippedHeaders)
                    responseBuilder.body(realResponseBody)
                    response = responseBuilder.build()
                }
            }

            networkCaptureData = getNetworkCaptureData(request, response)
        }

        embrace.recordNetworkRequest(
            request.url.toString(),
            request.method,
            response.sentRequestAtMillis,
            response.receivedResponseAtMillis,
            0L,
            contentLength,
            response.code,
            request.header(embrace.traceIdHeader()),
            traceparent,
            networkCaptureData
        )
        return response
    }

    private fun getContentLengthFromHeader(networkResponse: Response): Long? {
        var contentLength: Long? = null
        val contentLengthHeaderValue = networkResponse.header(CONTENT_LENGTH_HEADER_NAME, null)
        if (contentLengthHeaderValue != null) {
            try {
                contentLength = contentLengthHeaderValue.toLong()
            } catch (ex: Exception) {
                // Ignore
            }
        }
        return contentLength
    }

    private fun getContentLengthFromBody(networkResponse: Response, contentType: String?): Long? {
        var contentLength: Long? = null

        // Tolerant of a charset specified in header, e.g. Content-Type: text/event-stream;charset=UTF-8
        val serverSentEvent = contentType != null && contentType.startsWith(
            CONTENT_TYPE_EVENT_STREAM
        )
        if (!serverSentEvent) {
            try {
                val body = networkResponse.body
                if (body != null) {
                    val source = body.source()
                    source.request(Long.MAX_VALUE)
                    contentLength = source.buffer.size
                }
            } catch (ex: Exception) {
                // Ignore
            }
        }

        return contentLength
    }

    private fun getNetworkCaptureData(request: Request, response: Response): OkhttpCapturedData {
        var requestHeaders: Map<String, String>? = null
        var requestQueryParams: String? = null
        var responseHeaders: Map<String, String>? = null
        var requestBodyBytes: ByteArray? = null
        var responseBodyBytes: ByteArray? = null
        var dataCaptureErrorMessage: String? = null
        var partsAcquired = 0
        try {
            responseHeaders = getProcessedHeaders(response.headers.toMultimap())
            partsAcquired++
            requestHeaders = getProcessedHeaders(request.headers.toMultimap())
            partsAcquired++
            requestQueryParams = request.url.query
            partsAcquired++
            requestBodyBytes = getRequestBody(request)
            partsAcquired++
            if (response.promisesBody()) {
                val responseBody = response.body
                if (responseBody != null) {
                    val okResponseBodySource = responseBody.source()
                    okResponseBodySource.request(Int.MAX_VALUE.toLong())
                    responseBodyBytes = okResponseBodySource.buffer.snapshot().toByteArray()
                }
            }
        } catch (e: Exception) {
            val errors = StringBuilder()
            var i = partsAcquired
            while (i < 5) {
                errors.append("'").append(networkCallDataParts[i]).append("'")
                if (i != 4) {
                    errors.append(", ")
                }
                i++
            }
            dataCaptureErrorMessage = "There were errors in capturing the following part(s) of the network call: %s$errors"
            embrace.logInternalException(
                RuntimeException("Failure during the building of NetworkCaptureData. $dataCaptureErrorMessage", e)
            )
        }
        return OkhttpCapturedData(
            requestHeaders,
            requestQueryParams,
            requestBodyBytes,
            responseHeaders,
            responseBodyBytes,
            dataCaptureErrorMessage
        )
    }

    private fun getProcessedHeaders(properties: Map<String, List<String>>): Map<String, String> {
        val headers = HashMap<String, String>()
        for ((key, value1) in properties) {
            val builder = StringBuilder()
            for (value in value1) {
                builder.append(value)
            }
            headers[key] = builder.toString()
        }
        return headers
    }

    private fun getRequestBody(request: Request): ByteArray? {
        try {
            val requestCopy = request.newBuilder().build()
            val requestBody = requestCopy.body
            if (requestBody != null) {
                val buffer = Buffer()
                requestBody.writeTo(buffer)
                return buffer.readByteArray()
            }
        } catch (e: IOException) {
            embrace.logInternalError("Failed to capture okhttp request body.", e.javaClass.toString())
        }
        return null
    }



    internal companion object {
        internal const val ENCODING_GZIP = "gzip"
        internal const val CONTENT_LENGTH_HEADER_NAME = "Content-Length"
        internal const val CONTENT_ENCODING_HEADER_NAME = "Content-Encoding"
        internal const val CONTENT_TYPE_HEADER_NAME = "Content-Type"
        internal const val CONTENT_TYPE_EVENT_STREAM = "text/event-stream"
        internal const val TRACEPARENT_HEADER_NAME = "traceparent"
        private val networkCallDataParts = arrayOf(
            "Response Headers",
            "Request Headers",
            "Query Parameters",
            "Request Body",
            "Response Body"
        )
    }
}

/**
 * The additional data captured if network body capture is enabled for the URL
 */
data class OkhttpCapturedData(
    val requestHeaders: Map<String, String>?,
    val requestQueryParams: String?,
    val capturedRequestBody: ByteArray?,
    val responseHeaders: Map<String, String>?,
    val capturedResponseBody: ByteArray?,
    val dataCaptureErrorMessage: String? = null
) {
    val requestBodySize: Int
        get() = capturedRequestBody?.size ?: 0

    val responseBodySize: Int
        get() = capturedResponseBody?.size ?: 0
}
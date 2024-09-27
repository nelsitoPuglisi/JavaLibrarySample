package io.embrace.lib

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * This interceptor will only intercept errors that client app experiences.
 *
 * We used OkHttp application interceptor in this case because this interceptor
 * will be added first in the OkHttp3 interceptors stack. This allows us to catch network errors.
 * OkHttp network interceptors are added almost at the end of stack, they are closer to "the wire"
 * so they are not able to see network errors.
 *
 * We used the [EmbraceCustomPathException] to capture the custom path added in the interceptor
 * chain process for client errors on requests to a generic URL like a GraphQL endpoint.
 */
class EmbraceOkHttp3ApplicationInterceptor internal constructor(
    private val embrace: EmbraceAbrastraction
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val startTime = System.currentTimeMillis()
        val request: Request = chain.request()
        return try {
            // we are not interested in response, just proceed
            chain.proceed(request)
       } catch (e: Exception) {
            // we are interested in errors.
            if (embrace.isStarted()) {
                embrace.recordIncompleteRequest(
                    request.url.toString(),
                    request.method,
                    startTime,
                    causeName(e, UNKNOWN_EXCEPTION),
                    causeMessage(e, UNKNOWN_MESSAGE),
                    request.header(embrace.traceIdHeader()),
                    request.header(TRACEPARENT_HEADER_NAME)
                )
            }
            throw e
        }
    }

    internal companion object {
        internal const val TRACEPARENT_HEADER_NAME = "traceparent"
        internal const val UNKNOWN_EXCEPTION = "Unknown"
        internal const val UNKNOWN_MESSAGE = "An error occurred during the execution of this network request"

        /**
         * Return the canonical name of the cause of a [Throwable]. Handles null elements throughout,
         * including the throwable and its cause, in which case [defaultName] is returned
         */
        internal fun causeName(throwable: Throwable?, defaultName: String = ""): String {
            return throwable?.cause?.javaClass?.canonicalName ?: defaultName
        }

        /**
         * Return the message of the cause of a [Throwable]. Handles null elements throughout,
         * including the throwable and its cause, in which case [defaultMessage] is returned
         */
        internal fun causeMessage(throwable: Throwable?, defaultMessage: String = ""): String {
            return throwable?.cause?.message ?: defaultMessage
        }
    }
}

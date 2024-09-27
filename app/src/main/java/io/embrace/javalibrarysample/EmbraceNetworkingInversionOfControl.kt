package io.embrace.javalibrarysample

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.lib.EmbraceAbrastraction
import io.embrace.lib.OkhttpCapturedData
import kotlin.math.abs

class EmbraceNetworkingInversionOfControl(val embrace: Embrace) {

    val embraceAbrastraction: EmbraceAbrastraction

    init {
        embraceAbrastraction = EmbraceAbrastraction(
            { url, httpMethod, startTime, endTime, bytesSent, bytesReceived, statusCode, traceId, w3cTraceparent, capturedData ->

                val offset = sdkClockOffset()
                embrace.recordNetworkRequest(
                    EmbraceNetworkRequest.fromCompletedRequest(
                        url,
                        HttpMethod.fromString(httpMethod),
                        startTime + offset,
                        endTime + offset,
                        bytesSent,
                        bytesReceived,
                        statusCode,
                        traceId,
                        w3cTraceparent,
                        capturedData?.toEmbraceData()
                    )
                )
            },
            { url, httpMethod, startTime, errorType, errorMessage, traceId, w3cTraceparent ->
                val offset = sdkClockOffset()
                embrace.recordNetworkRequest(
                    EmbraceNetworkRequest.fromIncompleteRequest(
                        url,
                        HttpMethod.fromString(httpMethod),
                        startTime + offset,
                        embrace.internalInterface.getSdkCurrentTime(),
                        errorType,
                        errorMessage,
                        traceId,
                        if (embrace.internalInterface.isNetworkSpanForwardingEnabled()) w3cTraceparent else null,
                        null
                    )
                )
            },
            embrace::isStarted,
            {
                val networkSpanForwardingEnabled = embrace.internalInterface.isNetworkSpanForwardingEnabled()
                var traceparent: String? = null
                if (networkSpanForwardingEnabled) {
                    traceparent = embrace.generateW3cTraceparent()
                }
                return@EmbraceAbrastraction traceparent
            },
            embrace::traceIdHeader,
            { message, details -> embrace.internalInterface.logInternalError(message, details) },
            { throwable -> embrace.internalInterface.logInternalError(throwable) },
            embrace.internalInterface::shouldCaptureNetworkBody
        )
    }

    /**
     * Estimate the difference between the current time returned by the SDK clock and the system clock, the latter of which is used by
     * OkHttp to determine timestamps
     */
    private fun sdkClockOffset(): Long {
        // To ensure that the offset is the result of clock drift, we take two samples and ensure that their difference is less than 1ms
        // before we use the value. A 1 ms difference between the samples is possible given it could be the result of the time
        // "ticking over" to the next millisecond, but given the calls take the order of microseconds, it should not go beyond that.
        //
        // Any difference that is greater than 1 ms is likely the result of a change to the system clock during this process, or some
        // scheduling quirk that makes the result not trustworthy. In that case, we simply don't return an offset.

        val sdkTime1 = embrace.internalInterface.getSdkCurrentTime()
        val systemTime1 = System.currentTimeMillis()
        val sdkTime2 = embrace.internalInterface.getSdkCurrentTime()
        val systemTime2 = System.currentTimeMillis()

        val diff1 = sdkTime1 - systemTime1
        val diff2 = sdkTime2 - systemTime2

        return if (abs(diff1 - diff2) <= 1L) {
            (diff1 + diff2) / 2
        } else {
            0L
        }
    }

}

private fun OkhttpCapturedData.toEmbraceData(): NetworkCaptureData? {
    return NetworkCaptureData(
        this.requestHeaders,
        this.requestQueryParams,
        this.capturedRequestBody,
        this.responseHeaders,
        this.capturedResponseBody,
        this.dataCaptureErrorMessage
    )
}
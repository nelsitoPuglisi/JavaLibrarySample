package io.embrace.lib

class EmbraceAbrastraction(
    val recordNetworkRequest: (
        String,
        String,
        Long,
        Long,
        Long,
        Long,
        Int,
        String?,
        String?,
        OkhttpCapturedData?
    ) -> Unit,
    val recordIncompleteRequest: (
        String,
        String,
        Long,
        String,
        String,
        String?,
        String?
    ) -> Unit,
    val isStarted: () -> Boolean,
    val generateW3cTraceparent: () -> String?,
    val traceIdHeader: () -> String,
    val logInternalError: (String, String) -> Unit,
    val logInternalException: (Throwable) -> Unit,
    val shouldCaptureNetworkBody: (String, String) -> Boolean
)
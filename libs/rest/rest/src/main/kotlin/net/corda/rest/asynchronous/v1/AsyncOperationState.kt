package net.corda.rest.asynchronous.v1

enum class AsyncOperationState {

    /**
     * The requested has been accepted by the platform
     */
    ACCEPTED,

    /**
     * The platform is processing the request.
     */
    IN_PROGRESS,

    /**
     * The request completed successfully.
     */
    SUCCEEDED,

    /**
     * The request failed to complete.
     */
    FAILED,

    /**
     * The request was aborted.
     */
    ABORTED
}

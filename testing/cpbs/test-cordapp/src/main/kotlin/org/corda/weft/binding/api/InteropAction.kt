package org.corda.weft.binding.api

import org.corda.weft.binding.api.InteropAction.ClientAction
import org.corda.weft.binding.api.InteropAction.ServerResponse
import org.corda.weft.facade.FacadeRequest
import org.corda.weft.facade.FacadeResponse

/**
 * The same type, [InteropAction], is returned by both Facade clients and Facade servers.
 *
 * Clients will return a [ClientAction], representing an interop request to be performed; when [result] is called, the
 * request is carried out and the result obtained.
 *
 * Servers will return a [ServerResponse], wrapping the result value directly.
 */
sealed class InteropAction<T> {

    /**
     * The result of carrying out the interop action.
     */
    abstract val result: T

    /**
     * An interop action that has not yet been carried out, but will be when the caller requests the [result].
     *
     * @param request The [FacadeRequest] to send to the server
     * @param processor An object that knows how to send a [FacadeRequest] to the server and obtain a [FacadeResponse]
     * @param responseInterpreter An object that knows how to translate a [FacadeResponse] into the [result] type
     */
    data class ClientAction<T>(
        private val request: FacadeRequest,
        private val processor: (FacadeRequest) -> FacadeResponse,
        private val responseInterpreter: (FacadeResponse) -> T): InteropAction<T>() {
        override val result: T get() = responseInterpreter(processor(request))
    }

    /**
     * The result of an [InteropAction] that has been performed by the server.
     *
     * @param result The [result] value that the server returned
     */
    data class ServerResponse<T>(override val result: T): InteropAction<T>()

}
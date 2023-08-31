package net.corda.web.api

import net.corda.rest.ResponseCode

interface WebContext {

    /**
     * Sets the status code of the response.
     *
     * @param status The status code to be returned.
     */
    fun status(status: ResponseCode)

    /**
     * Retrieves the body of the request as a byte array.
     *
     * @return The body of the request as a byte array.
     */
    fun bodyAsBytes(): ByteArray

    /**
     * Retrieves the body of the request as a string.
     *
     * @return The body of the request as a string.
     */
    fun body(): String

    /**
     * Sets the result of the request.
     *
     * @param result The result to be set on the context.
     */
    fun result(result: Any)

    /**
     * Sets a header in the response with the specified name and value.
     *
     * @param header The name of the header to be set.
     * @param value The value of the header to be set.
     */
    fun header(header: String, value: String)

    /**
     * Retrieves the value of the specified header from the response.
     *
     * @param header The name of the header to be retrieved.
     */
    fun header(header: String)
}
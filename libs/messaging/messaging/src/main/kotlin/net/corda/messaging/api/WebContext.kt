package net.corda.messaging.api

interface WebContext {
    fun status(status: Int)

    fun bodyAsBytes(): ByteArray

    fun body(): String

    fun result(result: Any)

    fun header(header: String, value: String)

    fun header(header: String)
}
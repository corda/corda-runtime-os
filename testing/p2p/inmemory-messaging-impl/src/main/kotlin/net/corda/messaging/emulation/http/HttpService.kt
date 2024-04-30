package net.corda.messaging.emulation.http

import java.net.URI

interface HttpService {
    fun send(
        uri: URI,
        data: Any,
    ): Any?

    fun listen(
        endpoint: String,
        handler: (Any) -> Any?,
    )

    fun forget(
        endpoint: String,
    )
}

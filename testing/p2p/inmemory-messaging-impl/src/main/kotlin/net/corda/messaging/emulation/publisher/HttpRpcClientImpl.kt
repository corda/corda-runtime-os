package net.corda.messaging.emulation.publisher

import net.corda.messaging.api.publisher.HttpRpcClient
import java.net.URI

class HttpRpcClientImpl : HttpRpcClient {
    override fun <T : Any, R : Any> send(uri: URI, requestBody: T, clz: Class<R>): R? {
        throw NotImplementedError()
    }
}
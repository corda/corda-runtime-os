package net.corda.messaging.emulation.publisher

import net.corda.messaging.api.publisher.HttpRpcClient
import net.corda.messaging.emulation.http.HttpService
import java.net.URI

class HttpRpcClientImpl(
    private val httpService: HttpService,
) : HttpRpcClient {
    override fun <T : Any, R : Any> send(uri: URI, requestBody: T, clz: Class<R>): R? {
        @Suppress("UNCHECKED_CAST")
        return httpService.send(uri, requestBody) as? R
    }
}

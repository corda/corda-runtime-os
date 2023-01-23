package net.corda.httprpc.client

import net.corda.httprpc.RestResource
import net.corda.httprpc.client.auth.credentials.CredentialsProvider

/**
 * Can be attached to [HttpRpcClient] to be informed about connection and disconnection events.
 */
interface HttpRpcConnectionListener<I : RestResource> {
    interface HttpRpcConnectionContext<I : RestResource> {
        val credentialsProvider: CredentialsProvider
        val connectionOpt: HttpRpcConnection<I>?
        val throwableOpt: Throwable?
    }

    fun onConnect(context: HttpRpcConnectionContext<I>)

    fun onDisconnect(context: HttpRpcConnectionContext<I>)

    fun onPermanentFailure(context: HttpRpcConnectionContext<I>)
}

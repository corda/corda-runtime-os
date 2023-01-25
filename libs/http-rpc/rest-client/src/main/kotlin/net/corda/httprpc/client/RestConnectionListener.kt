package net.corda.httprpc.client

import net.corda.httprpc.RestResource
import net.corda.httprpc.client.auth.credentials.CredentialsProvider

/**
 * Can be attached to [RestClient] to be informed about connection and disconnection events.
 */
interface RestConnectionListener<I : RestResource> {
    interface RestConnectionContext<I : RestResource> {
        val credentialsProvider: CredentialsProvider
        val connectionOpt: RestConnection<I>?
        val throwableOpt: Throwable?
    }

    fun onConnect(context: RestConnectionContext<I>)

    fun onDisconnect(context: RestConnectionContext<I>)

    fun onPermanentFailure(context: RestConnectionContext<I>)
}

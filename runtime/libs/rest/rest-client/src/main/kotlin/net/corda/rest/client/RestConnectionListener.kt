package net.corda.rest.client

import net.corda.rest.RestResource
import net.corda.rest.client.auth.credentials.CredentialsProvider

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

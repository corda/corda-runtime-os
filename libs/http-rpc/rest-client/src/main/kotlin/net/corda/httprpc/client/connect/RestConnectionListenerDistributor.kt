package net.corda.httprpc.client.connect

import net.corda.httprpc.RestResource
import net.corda.httprpc.client.RestConnection
import net.corda.httprpc.client.RestConnectionListener
import net.corda.httprpc.client.auth.credentials.CredentialsProvider
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [RestConnectionListenerDistributor] is responsible for distributing connection
 * and disconnection events to interested listeners ([RestConnectionListener]).
 */
class RestConnectionListenerDistributor<I : RestResource>
    (private val listeners: Iterable<RestConnectionListener<I>>, private val credentialsProvider: CredentialsProvider) {
    companion object {
        private val log = contextLogger()

        private data class RestConnectionContextImpl<I : RestResource>(
            override val credentialsProvider: CredentialsProvider,
            override val connectionOpt: RestConnection<I>?,
            override val throwableOpt: Throwable?
        ) : RestConnectionListener.RestConnectionContext<I>
    }

    @Volatile
    internal var connectionOpt: RestConnection<I>? = null

    private val connected = AtomicBoolean()

    internal fun onConnect() {
        log.trace { "On connect." }
        if (connected.compareAndSet(false, true)) {
            safeForEachListener {
                onConnect(RestConnectionContextImpl(credentialsProvider, connectionOpt, null))
            }
        } else {
            log.debug { "Not distributing onConnect as already connected." }
        }
        log.trace { "On connect completed." }
    }

    internal fun onDisconnect(throwableOpt: Throwable?) {
        log.trace { "On disconnect." }
        if (connectionOpt != null) {
            if (connected.compareAndSet(true, false)) {
                safeForEachListener {
                    onDisconnect(RestConnectionContextImpl(credentialsProvider, connectionOpt, throwableOpt))
                }
            } else {
                log.debug { "Not distributing onDisconnect as already disconnected." }
            }
        } else {
            log.debug { "Not distributing onDisconnect as connection has never been established." }
        }
        log.trace { "On disconnect completed." }
    }

    internal fun onPermanentFailure(throwableOpt: Throwable?) {
        log.trace { "On permanent failure." }
        safeForEachListener {
            onPermanentFailure(RestConnectionContextImpl(credentialsProvider, connectionOpt, throwableOpt))
        }
        log.trace { "On permanent failure completed." }
    }

    private fun safeForEachListener(action: RestConnectionListener<I>.() -> Unit) {
        log.trace { "Safe for each listener." }
        listeners.forEach {
            try {
                it.action()
            } catch (ex: Exception) {
                log.error("Exception during distribution to: $it", ex)
            }
        }
        log.trace { "Safe for each listener completed." }
    }
}

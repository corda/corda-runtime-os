package net.corda.p2p.gateway.messaging

import net.corda.messaging.api.subscription.LifeCycle
import net.corda.v5.base.util.NetworkHostAndPort
import org.slf4j.LoggerFactory

/**
 * The [ConnectionManager] is responsible for creating an HTTP connection and caching it. If a connection to the requested
 * target already exists, it's reused. There will be a maximum 100 connections allowed at any given time. Any new requests
 * will block until resources become available.
 *
 * To ensure we don't block indefinitely, several timeouts will be used to determine when to close an inactive connection
 * or to drop a request for one.
 *
 */
class ConnectionManager(private val sslConfig: SslConfiguration) : LifeCycle {

    private val logger = LoggerFactory.getLogger(ConnectionManager::class.java)

    companion object {
        /**
         * Maximum size of the connection pool
         */
        private const val MAX_CONNECTIONS = 100

        /**
         * Time in milliseconds after which a connection request will fail
         */
        private const val ACQUIRE_TIMEOUT = 60000

        /**
         * Time in milliseconds after which an inactive connection in the pool will be released (closed)
         */
        private const val CONNECTION_MAX_IDLE_TIME = 60000
    }

    override fun start() {

    }

    override fun stop() {

    }

    fun createConnection(destination: NetworkHostAndPort): NetworkHostAndPort {
        TODO("$destination")
    }
}
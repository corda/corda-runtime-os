package net.corda.httprpc.client

import net.corda.httprpc.RestResource
import net.corda.httprpc.client.config.RestClientConfig
import net.corda.httprpc.client.connect.RestClientProxyHandler
import net.corda.httprpc.client.connect.RestConnectionListenerDistributor
import net.corda.httprpc.client.connect.remote.RemoteUnirestClient
import net.corda.utilities.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import net.corda.v5.base.util.uncheckedCast
import org.slf4j.Logger
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.concurrent.scheduleAtFixedRate

/**
 * [RestClient] is meant to run outside of Corda Node JVM and provide connectivity to a node using the REST
 * protocol. Since Corda Node can expose multiple interfaces through HTTP, it is required to specify which
 * [RestResource] interface should be used.
 *
 * @property baseAddress The base address of the server.
 * @property restResourceClass The [RestResource] interface for which the proxy will be created.
 * @property clientConfig The configuration for the client to use.
 * @property healthCheckInterval The interval on which health check calls to the server will happen, ensuring
 *                               connectivity.
 */
@Suppress("LongParameterList")
class RestClient<I : RestResource> internal constructor(
    private val baseAddress: String,
    private val restResourceClass: Class<I>,
    private val clientConfig: RestClientConfig,
    private val healthCheckInterval: Long,
    private val proxyGenerator: (rpcOpsClass: Class<I>, proxyHandler: RestClientProxyHandler<I>) -> I
) : AutoCloseable {

    constructor(
        baseAddress: String,
        restResourceClass: Class<I>,
        clientConfig: RestClientConfig
    ) : this(baseAddress, restResourceClass, clientConfig, defaultHealthCheckInterval, Companion::defaultProxyGenerator)

    constructor(
        baseAddress: String,
        restResourceClass: Class<I>,
        clientConfig: RestClientConfig,
        healthCheckInterval: Long
    ) : this(baseAddress, restResourceClass, clientConfig, healthCheckInterval, Companion::defaultProxyGenerator)

    private companion object {
        private val log = contextLogger()

        private const val defaultHealthCheckInterval = 10000L

        private fun <I : RestResource> defaultProxyGenerator(
            rpcOpsClass: Class<I>, proxyHandler: RestClientProxyHandler<I>): I =
            uncheckedCast(Proxy.newProxyInstance(rpcOpsClass.classLoader, arrayOf(rpcOpsClass), proxyHandler))

        private fun <T> Logger.logElapsedTime(label: String, body: () -> T): T = logElapsedTime(label, this, body)

        private fun <T> logElapsedTime(label: String, logger: Logger, body: () -> T): T {
            // Use nanoTime as it's monotonic.
            val now = System.nanoTime()
            var failed = false
            try {
                return body()
            } catch (th: Throwable) {
                failed = true
                throw th
            } finally {
                val elapsed = Duration.ofNanos(System.nanoTime() - now).toMillis()
                val msg = (if (failed) "Failed " else "") + "$label took $elapsed msec"
                logger.info(msg)
            }
        }
    }

    private val listeners: MutableSet<RestConnectionListener<I>> = CopyOnWriteArraySet()
    private val connectionEventDistributor = RestConnectionListenerDistributor(
        listeners, clientConfig.authenticationConfig.getCredentialsProvider()
    )
    private var serverProtocolVersion: Int? = null
    private lateinit var healthCheckTimer: Timer

    @VisibleForTesting
    internal lateinit var ops: I

    fun start(): RestConnection<I> {
        log.trace { "Start." }
        return log.logElapsedTime("REST client") {
            val proxyHandler = RestClientProxyHandler(
                RemoteUnirestClient(baseAddress, clientConfig.enableSSL), clientConfig.authenticationConfig, restResourceClass
            )
            try {
                ops = proxyGenerator(restResourceClass, proxyHandler)
                serverProtocolVersion = ops.protocolVersion
                if (serverProtocolVersion!! < clientConfig.minimumServerProtocolVersion) {
                    throw IllegalArgumentException(
                        "Requested minimum protocol version " +
                                "(${clientConfig.minimumServerProtocolVersion}) is higher" +
                                " than the server's supported protocol version ($serverProtocolVersion)"
                    )
                }
                proxyHandler.setServerProtocolVersion(serverProtocolVersion!!)

                log.debug { "REST client connected, returning proxy" }
                object : RestConnection<I> {
                    override val proxy: I
                        get() = ops
                    override val serverProtocolVersion: Int
                        get() = this@RestClient.serverProtocolVersion!!
                }.also {
                    connectionEventDistributor.connectionOpt = it
                    // Up above we made a successful call to `ops.protocolVersion` so we are in position to notify
                    // listeners that connection is active.
                    connectionEventDistributor.onConnect()
                    healthCheckTimer = schedulePeriodicHealthCheck()
                }
            } catch (throwable: Throwable) {
                log.error("Unexpected error when starting", throwable)
                connectionEventDistributor.onPermanentFailure(throwable)
                throw throwable
            }
        }.also { log.trace { "Start completed." } }
    }

    fun addConnectionListener(listener: RestConnectionListener<I>): Boolean {
        log.trace { """"Add connection listener "${listener.javaClass.simpleName}".""" }
        return listeners.add(listener)
    }

    fun removeConnectionListener(listener: RestConnectionListener<I>): Boolean {
        log.trace { """Remove connection listener "${listener.javaClass.simpleName}".""" }
        return listeners.remove(listener)
    }

    override fun close() {
        log.trace { "Close." }
        if (this::healthCheckTimer.isInitialized) healthCheckTimer.cancel()
        connectionEventDistributor.onDisconnect(null)
        log.trace { "Close completed." }
    }

    private fun schedulePeriodicHealthCheck(): Timer {
        log.trace { "Schedule periodic health check." }
        val timer = Timer("KeepAliveRestClient Health Check", true)
        timer.scheduleAtFixedRate(0, healthCheckInterval) {
            if (healthCheck()) {
                log.debug { "Connection stable." }
            } else {
                log.warn("Did not get health check response.")
            }
        }
        return timer
            .also { log.trace { "Schedule periodic health check completed." } }
    }

    @Suppress("TooGenericExceptionThrown")
    private fun healthCheck(): Boolean =
        try {
            if (ops.protocolVersion != serverProtocolVersion) {
                log.warn(
                    "Protocol version previously retrieved (${ops.protocolVersion}) does not match" +
                            " the initial protocol version ($serverProtocolVersion)"
                )
            }
            connectionEventDistributor.onConnect()
            true
        } catch (throwable: Throwable) {
            log.error("Error when performing health check call", throwable)
            connectionEventDistributor.onDisconnect(throwable)
            false
        }
}

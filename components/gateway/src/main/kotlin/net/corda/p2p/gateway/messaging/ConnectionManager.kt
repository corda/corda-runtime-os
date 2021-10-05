package net.corda.p2p.gateway.messaging

import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.p2p.gateway.domino.ConfigurationAwareTile
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The [ConnectionManager] is responsible for creating an HTTP connection and caching it. If a connection to the requested
 * target already exists, it's reused. There will be a maximum 100 connections allowed at any given time. Any new requests
 * will block until resources become available.
 *
 * To ensure we don't block indefinitely, several timeouts will be used to determine when to close an inactive connection
 * or to drop a request for one.
 *
 */
class ConnectionManager(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
    private val listener: HttpEventListener,
) : ConfigurationAwareTile(lifecycleCoordinatorFactory, configurationReaderService) {

    companion object {
        private const val NUM_CLIENT_WRITE_THREADS = 2
        private const val NUM_CLIENT_NETTY_THREADS = 2
    }

    private val clientPool = ConcurrentHashMap<URI, HttpClient>()
    private var writeGroup: EventLoopGroup? = null
    private var nettyGroup: EventLoopGroup? = null

    private val lock = ReentrantLock()
    private val waitForConfiguration = lock.newCondition()
    @Volatile
    private var sslConfiguration: SslConfiguration? = null

    /**
     * Return an existing or new [HttpClient].
     * @param destinationInfo the [DestinationInfo] object containing the destination's URI, SNI, and legal name
     */
    fun acquire(destinationInfo: DestinationInfo): HttpClient {
        if(sslConfiguration == null) {
            lock.withLock {
                if(sslConfiguration == null) {
                    if(!waitForConfiguration.await(10, TimeUnit.MINUTES)) {
                        throw IllegalStateException("Waiting too long for configuration")
                    }
                }
            }
        }

        return clientPool.computeIfAbsent(destinationInfo.uri) {
            val client = HttpClient(
                destinationInfo,
                sslConfiguration!!,
                writeGroup!!,
                nettyGroup!!,
                listener
            )
            executeBeforeStop(client::close)
            client.start()
            client
        }
    }

    override fun applyNewConfiguration(
        newConfiguration: GatewayConfiguration,
        oldConfiguration: GatewayConfiguration?
    ) {
        if (newConfiguration.sslConfig != oldConfiguration?.sslConfig) {
            val oldClients = clientPool.toMap()
            clientPool.clear()
            oldClients.values.forEach {
                it.close()
            }
            lock.withLock {
                sslConfiguration = newConfiguration.sslConfig
                waitForConfiguration.signalAll()
            }
        }
    }

    override fun createResources() {
        NioEventLoopGroup(NUM_CLIENT_WRITE_THREADS).also {
            executeBeforeStop {
                it.shutdownGracefully()
                it.terminationFuture().sync()
            }
        }.also { writeGroup = it }
        nettyGroup = NioEventLoopGroup(NUM_CLIENT_NETTY_THREADS).also {
            executeBeforeStop {
                it.shutdownGracefully()
                it.terminationFuture().sync()
            }
        }
        executeBeforeStop(clientPool::clear)
        super.createResources()
    }
}

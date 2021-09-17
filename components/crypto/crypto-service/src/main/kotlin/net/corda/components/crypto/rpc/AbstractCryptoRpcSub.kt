package net.corda.components.crypto.rpc

import net.corda.crypto.impl.lifecycle.NewCryptoConfigReceived
import net.corda.crypto.impl.config.CryptoLibraryConfig
import net.corda.crypto.impl.lifecycle.CryptoLifecycleComponent
import net.corda.messaging.api.subscription.RPCSubscription
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class AbstractCryptoRpcSub<TREQ: Any, TRESP: Any> : CryptoLifecycleComponent {

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private var subscription: RPCSubscription<TREQ, TRESP>? = null

    abstract fun createSubscription(libraryConfig: CryptoLibraryConfig): RPCSubscription<TREQ, TRESP>

    private val lock = ReentrantLock()

    override var isRunning: Boolean = false

    override fun start() = lock.withLock {
        logger.info("Starting...")
        isRunning = true
    }

    override fun stop() = lock.withLock {
        logger.info("Stopping...")
        subscription?.stop()
        isRunning = false
    }

    override fun handleConfigEvent(event: NewCryptoConfigReceived): Unit = lock.withLock {
        subscription?.stop()
        logger.info("Creating durable subscription")
        subscription = createSubscription(event.config)
        logger.info("Starting durable subscription")
        subscription?.start()
    }
}
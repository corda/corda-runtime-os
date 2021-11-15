package net.corda.crypto.service.rpc

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractCryptoRpcSub<REQUEST: Any, RESPONSE: Any> : Lifecycle, CryptoLifecycleComponent {

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private var subscription: RPCSubscription<REQUEST, RESPONSE>? = null

    abstract fun createSubscription(libraryConfig: CryptoLibraryConfig): RPCSubscription<REQUEST, RESPONSE>

    override var isRunning: Boolean = false

    override fun start() {
        logger.info("Starting...")
        isRunning = true
    }

    override fun stop() {
        logger.info("Stopping...")
        subscription?.stop()
        isRunning = false
    }

    override fun handleConfigEvent(config: CryptoLibraryConfig) {
        subscription?.stop()
        logger.info("Creating durable subscription")
        subscription = createSubscription(config)
        logger.info("Starting durable subscription")
        subscription?.start()
    }
}
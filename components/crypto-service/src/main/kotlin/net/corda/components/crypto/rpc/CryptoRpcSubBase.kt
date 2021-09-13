package net.corda.components.crypto.rpc

import net.corda.cipher.suite.impl.lifecycle.CryptoServiceLifecycleEventHandler
import net.corda.cipher.suite.impl.lifecycle.CryptoLifecycleComponent
import net.corda.cipher.suite.impl.config.CryptoConfigEvent
import net.corda.cipher.suite.impl.config.CryptoLibraryConfig
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.subscription.RPCSubscription
import kotlin.concurrent.withLock

abstract class CryptoRpcSubBase<TREQ: Any, TRESP: Any>(
    cryptoServiceLifecycleEventHandler: CryptoServiceLifecycleEventHandler
) : CryptoLifecycleComponent(cryptoServiceLifecycleEventHandler) {

    private var subscription: RPCSubscription<TREQ, TRESP>? = null

    abstract fun createSubscription(libraryConfig: CryptoLibraryConfig): RPCSubscription<TREQ, TRESP>

    override fun stop() = lock.withLock {
        subscription?.stop()
        super.stop()
    }

    override fun handleLifecycleEvent(event: LifecycleEvent) = lock.withLock {
        logger.info("LifecycleEvent received: $event")
        when (event) {
            is CryptoConfigEvent -> {
                logger.info("Received config event {}", event::class.qualifiedName)
                reset(event.config)
            }
            is StopEvent -> {
                stop()
                logger.info("Received stop event")
            }
        }
    }

    private fun reset(config: CryptoLibraryConfig) {
        subscription?.stop()
        logger.info("Creating durable subscription")
        subscription = createSubscription(config)
        logger.info("Starting durable subscription")
        subscription?.start()
    }
}
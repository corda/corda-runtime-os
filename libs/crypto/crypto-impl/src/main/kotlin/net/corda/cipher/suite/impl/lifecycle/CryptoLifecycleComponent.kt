package net.corda.cipher.suite.impl.lifecycle

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class CryptoLifecycleComponent(
    private val cryptoServiceLifecycleEventHandler: CryptoServiceLifecycleEventHandler
) : Lifecycle {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    protected val lock = ReentrantLock()

    override var isRunning: Boolean = false

    override fun start() = lock.withLock {
        if (!isRunning) {
            logger.info("Starting...")
            cryptoServiceLifecycleEventHandler.add { event, _ -> handleLifecycleEvent(event) }
            isRunning = true
        } else {
            logger.warn("The component is already running...")
        }
    }

    override fun stop() = lock.withLock {
        isRunning = false
        logger.info("Stopped...")
    }

    protected abstract fun handleLifecycleEvent(event: LifecycleEvent)
}

@Suppress("TooGenericExceptionCaught")
fun AutoCloseable.closeGracefully() {
    try {
        close()
    } catch (e: Throwable) {
        // intentional
    }
}

fun MutableMap<*, *>.clearCache() {
    forEach {
        (it.value as? AutoCloseable)?.closeGracefully()
    }
    clear()
}
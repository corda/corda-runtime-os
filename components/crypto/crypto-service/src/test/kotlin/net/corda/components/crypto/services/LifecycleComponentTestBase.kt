package net.corda.components.crypto.services

import net.corda.components.crypto.CryptoCoordinator
import net.corda.crypto.impl.lifecycle.AbstractCryptoLifecycleComponent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

abstract class LifecycleComponentTestBase {
    private val latchTimeout = 2000L
    private lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var readyLatch: CountDownLatch
    private lateinit var stopLatch: CountDownLatch
    private lateinit var startLatch: CountDownLatch
    private lateinit var libraryCoordinator: LifecycleCoordinator
    protected lateinit var cryptoServiceLifecycleEventHandler: CryptoServiceLifecycleEventHandler

    protected fun setupCoordinator() {
        readyLatch = CountDownLatch(1)
        stopLatch = CountDownLatch(1)
        startLatch = CountDownLatch(1)
        cryptoServiceLifecycleEventHandler = CryptoServiceLifecycleEventHandler()
        cryptoServiceLifecycleEventHandler.add { event, _ ->
            when (event) {
                is ReadyEvent -> readyLatch.countDown()
                is StartEvent -> startLatch.countDown()
                is StopEvent -> stopLatch.countDown()
            }
        }
        lifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl()
        libraryCoordinator = lifecycleCoordinatorFactory.createCoordinator<CryptoCoordinator>(
            cryptoServiceLifecycleEventHandler
        )
        libraryCoordinator.start()
        assertTrue(startLatch.await(latchTimeout, TimeUnit.MILLISECONDS))
    }

    protected fun stopCoordinator() {
        libraryCoordinator.stop()
        stopLatch.await(latchTimeout, TimeUnit.MILLISECONDS)
        libraryCoordinator.close()
    }

    protected fun <C : AbstractCryptoLifecycleComponent> createComponent(factory: () -> C): C {
        val component = factory()
        ready()
        return component
    }

    protected fun postEvent(event: LifecycleEvent) {
        libraryCoordinator.postEvent(event)
    }

    private fun ready() {
        libraryCoordinator.postEvent(ReadyEvent())
        assertTrue(readyLatch.await(latchTimeout, TimeUnit.MILLISECONDS))
    }

    class ReadyEvent : LifecycleEvent
}
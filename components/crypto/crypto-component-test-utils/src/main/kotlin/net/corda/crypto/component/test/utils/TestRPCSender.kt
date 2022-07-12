package net.corda.crypto.component.test.utils

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class TestRPCSender<REQUEST, RESPONSE>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    override val subscriptionName: LifecycleCoordinatorName = LifecycleCoordinatorName("TestSender"),
) : RPCSender<REQUEST, RESPONSE> {
    companion object {
        private val logger = contextLogger()
    }

    val lifecycleCoordinator = coordinatorFactory.createCoordinator(subscriptionName) { event, coordinator ->
        logger.info("LifecycleEvent received: $event")
        if(event is StartEvent) { coordinator.updateStatus(LifecycleStatus.UP) }
    }

    private var response: ((REQUEST) -> RESPONSE)? = null

    var stopped = AtomicInteger()

    var lastRequest: REQUEST? = null

    fun setupCompletedResponse(response: (REQUEST) -> RESPONSE) {
        this.response = response
    }

    override fun sendRequest(req: REQUEST): CompletableFuture<RESPONSE> {
        lastRequest = req
        val future = CompletableFuture<RESPONSE>()
        require(response != null)
        future.complete(response?.invoke(req))
        return future
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
        stopped.incrementAndGet()
    }
}

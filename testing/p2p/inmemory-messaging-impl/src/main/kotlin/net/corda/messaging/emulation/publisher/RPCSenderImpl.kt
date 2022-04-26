package net.corda.messaging.emulation.publisher

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.emulation.rpc.RPCTopicService
import java.util.concurrent.CompletableFuture

class RPCSenderImpl<REQUEST, RESPONSE>(
    private val rpcConfig: RPCConfig<REQUEST, RESPONSE>,
    private val rpcTopicService: RPCTopicService,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val clientIdCounter: String
) : RPCSender<REQUEST, RESPONSE> {

    private var running = false
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(
        LifecycleCoordinatorName(
            "${rpcConfig.groupName}-RPCSender-${rpcConfig.requestTopic}",
            clientIdCounter
        )
    ) { _, _ -> }

    override val isRunning get() = running

    override fun start() {
        running = true
        lifecycleCoordinator.start()
        lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
    }

    override fun stop() {
        running = false
        lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        lifecycleCoordinator.stop()
    }

    override fun close() {
        running = false
        lifecycleCoordinator.close()
    }

    override fun sendRequest(req: REQUEST): CompletableFuture<RESPONSE> {
        if (!running) {
            throw CordaRPCAPISenderException("The sender has not been started")
        }
        return CompletableFuture<RESPONSE>().also {
            rpcTopicService.publish(rpcConfig.requestTopic, req, it)
        }
    }

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name
}

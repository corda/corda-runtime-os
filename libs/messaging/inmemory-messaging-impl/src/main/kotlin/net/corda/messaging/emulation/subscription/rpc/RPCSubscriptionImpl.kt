package net.corda.messaging.emulation.subscription.rpc

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.emulation.rpc.RPCTopicService

class RPCSubscriptionImpl<REQUEST, RESPONSE>(
    private val rpcConfig: RPCConfig<REQUEST, RESPONSE>,
    private val rpcTopicService: RPCTopicService,
    private val responderProcessor: RPCResponderProcessor<REQUEST, RESPONSE>,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : RPCSubscription<REQUEST, RESPONSE> {

    private var running = false

    override val isRunning get() = running

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name

    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(
        LifecycleCoordinatorName(
            "${rpcConfig.groupName}-RPCSubscription-${rpcConfig.requestTopic}",
            rpcConfig.instanceId.toString()
        )
    ) { _, _ -> }

    override fun start() {
        running = true
        rpcTopicService.subscribe(rpcConfig.requestTopic,responderProcessor)
        lifecycleCoordinator.start()
        lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
    }

    override fun stop() {
        unsubscribe()
        lifecycleCoordinator.stop()
    }

    override fun close() {
        unsubscribe()
        lifecycleCoordinator.close()
    }

    private fun unsubscribe() {
        rpcTopicService.unsubscribe(rpcConfig.requestTopic,responderProcessor)
        running = false
        lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
    }
}

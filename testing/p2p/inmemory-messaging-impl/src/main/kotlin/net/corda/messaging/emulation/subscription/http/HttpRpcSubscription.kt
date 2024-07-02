package net.corda.messaging.emulation.subscription.http

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import net.corda.messaging.emulation.http.HttpService

internal class HttpRpcSubscription<REQUEST, RESPONSE>(
    private val httpService: HttpService,
    private val rpcConfig: SyncRPCConfig,
    private val processor: SyncRPCProcessor<REQUEST, RESPONSE>,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    instanceId: Int,
) : RPCSubscription<REQUEST, RESPONSE> {
    override val subscriptionName = LifecycleCoordinatorName(
        componentName = "${rpcConfig.name}-${rpcConfig.endpoint}",
        instanceId = instanceId.toString(),
    )
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(
        subscriptionName,
    ) { _, _ -> }

    override fun start() {
        lifecycleCoordinator.start()
        lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        httpService.listen(rpcConfig.endpoint) {
            @Suppress("UNCHECKED_CAST")
            val request = it as? REQUEST
            if (request != null) {
                processor.process(request)
            } else {
                null
            }
        }
    }

    override fun close() {
        httpService.forget(rpcConfig.endpoint)
        lifecycleCoordinator.close()
    }
}

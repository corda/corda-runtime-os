package net.corda.lifecycle.domino.logic.util

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.read

class RPCSenderWithDominoLogic<REQUEST : Any, RESPONSE : Any>(
    publisherFactory: PublisherFactory,
    coordinatorFactory: LifecycleCoordinatorFactory,
    publisherConfig: RPCConfig<REQUEST, RESPONSE>,
    messagingConfiguration: SmartConfig,
): PublisherWithDominoLogicBase<RPCSender<REQUEST, RESPONSE>> (coordinatorFactory, {
    val sender = publisherFactory.createRPCSender(publisherConfig, messagingConfiguration)
    sender.start()
    sender
}) {
    fun sendRequest(request: REQUEST): CompletableFuture<RESPONSE> {
        return lifecycleLock.read {
            publisher.get().sendRequest(request)
        }
    }
}
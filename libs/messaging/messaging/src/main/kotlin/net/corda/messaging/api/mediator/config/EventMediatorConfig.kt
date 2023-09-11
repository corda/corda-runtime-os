package net.corda.messaging.api.mediator.config

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.api.mediator.factory.MediatorProducerFactory
import net.corda.messaging.api.processor.StateAndEventProcessor

/**
 * Class to store the required params to create a [MultiSourceEventMediator].
 *
 * @property name The unique name for a multi-source event mediator.
 * @property messagingConfig Messaging related configuration.
 * @property consumerFactories Factories for creating message consumers.
 * @property producerFactories Factories for creating message producers.
 * @property messageProcessor State and event processor.
 * @property messageRouter Message router that routes output messages of the state and event processor to producers.
 */
data class EventMediatorConfig<K: Any, S: Any, E: Any>(
    val name: String,
    val messagingConfig : SmartConfig,
    val consumerFactories: Collection<MediatorConsumerFactory>,
    val producerFactories: Collection<MediatorProducerFactory>,
    val messageProcessor : StateAndEventProcessor<K, S, E>,
    val messageRouter: MessageRouter,
)
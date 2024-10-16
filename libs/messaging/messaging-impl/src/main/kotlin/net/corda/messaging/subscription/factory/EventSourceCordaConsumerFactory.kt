package net.corda.messaging.subscription.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.constants.ConsumerRoles
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messaging.api.subscription.listener.ConsumerOffsetProvider
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.messaging.subscription.consumer.listener.OffsetProviderListener
import org.slf4j.Logger

/**
 * The [EventSourceCordaConsumerFactory] is responsible for building a configured instance of a CordaConsumer
 */
@Suppress("LongParameterList")
class EventSourceCordaConsumerFactory<K : Any, V : Any>(
    private val group: String,
    private val clientId: String,
    private val topic: String,
    private val keyClass: Class<K>,
    private val valueClass: Class<V>,
    private val messageBusConfig: SmartConfig,
    private val partitionAssignmentListener: PartitionAssignmentListener?,
    private val consumerOffsetProvider: ConsumerOffsetProvider?,
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    private val log: Logger
) {
    fun create(): CordaConsumer<K, V> {
        val consumerConfig = ConsumerConfig(group, clientId, ConsumerRoles.EVENT_SOURCE)

        return cordaConsumerBuilder.createConsumer<K, V>(
            consumerConfig,
            messageBusConfig,
            keyClass,
            valueClass,
            onSerializationError = { _ ->
                log.error("Failed to deserialize record for topic=$topic")
            }
        ).apply {
            setDefaultRebalanceListener(
                OffsetProviderListener(
                    clientId,
                    partitionAssignmentListener,
                    consumerOffsetProvider,
                    this
                )
            )
            subscribe(topic)
        }
    }
}
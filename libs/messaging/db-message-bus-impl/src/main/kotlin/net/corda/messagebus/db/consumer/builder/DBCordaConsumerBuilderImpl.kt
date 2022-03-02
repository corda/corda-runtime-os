package net.corda.messagebus.db.consumer.builder

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.builder.MessageBusConsumerBuilder
import net.corda.messagebus.db.consumer.DBCordaConsumerImpl
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

/**
 * Generate a DB-backed [CordaConsumer].
 */
@Component(service = [MessageBusConsumerBuilder::class])
class DBCordaConsumerBuilderImpl @Activate constructor(
) : MessageBusConsumerBuilder {
    override fun <K : Any, V : Any> createConsumer(
        consumerConfig: ConsumerConfig,
        busConfig: SmartConfig,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onSerializationError: (ByteArray) -> Unit,
        listener: CordaConsumerRebalanceListener?
    ): CordaConsumer<K, V> {
        return DBCordaConsumerImpl()
    }
}

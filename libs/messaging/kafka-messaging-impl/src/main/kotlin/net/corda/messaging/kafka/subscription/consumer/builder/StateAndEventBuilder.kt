package net.corda.messaging.kafka.subscription.consumer.builder

import com.typesafe.config.Config
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventConsumer
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener

interface StateAndEventBuilder<K : Any, S : Any, E : Any> {
    fun createProducer(config: Config): CordaKafkaProducer
    fun createStateEventConsumerAndRebalanceListener(
        config: Config,
        kClazz: Class<K>,
        sClazz: Class<S>,
        eClazz: Class<E>,
        stateAndEventListener: StateAndEventListener<K, S>? = null
    ): Pair<StateAndEventConsumer<K, S,E>, ConsumerRebalanceListener>
}

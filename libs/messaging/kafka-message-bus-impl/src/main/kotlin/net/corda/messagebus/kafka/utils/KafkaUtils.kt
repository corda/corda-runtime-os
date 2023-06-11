package net.corda.messagebus.kafka.utils

import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.messagebus.kafka.serialization.CordaAvroSerializerImpl
import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleWiring
import java.util.Properties

internal fun <K : Any, V : Any> createKafkaConsumer(
    kafkaProperties: Properties,
    keyDeserializer: CordaAvroDeserializerImpl<K>,
    valueDeserializer: CordaAvroDeserializerImpl<V>,
): KafkaConsumer<Any, Any> {
    val contextClassLoader = Thread.currentThread().contextClassLoader
    val currentBundle = FrameworkUtil.getBundle(KafkaConsumer::class.java)

    return try {
        if (currentBundle != null) {
            Thread.currentThread().contextClassLoader = currentBundle.adapt(BundleWiring::class.java).classLoader
        }
        KafkaConsumer(
            kafkaProperties,
            keyDeserializer,
            valueDeserializer
        )
    } finally {
        Thread.currentThread().contextClassLoader = contextClassLoader
    }
}

internal fun createKafkaProducer(
    kafkaProperties: Properties,
    onSerializationError: ((ByteArray) -> Unit)?,
    avroSchemaRegistry: AvroSchemaRegistry
): KafkaProducer<Any, Any> {
    val contextClassLoader = Thread.currentThread().contextClassLoader
    val currentBundle = FrameworkUtil.getBundle(KafkaProducer::class.java)

    return try {
        if (currentBundle != null) {
            Thread.currentThread().contextClassLoader = currentBundle.adapt(BundleWiring::class.java).classLoader
        }
        KafkaProducer(
            kafkaProperties,
            CordaAvroSerializerImpl(avroSchemaRegistry, onSerializationError),
            CordaAvroSerializerImpl(avroSchemaRegistry, onSerializationError)
        )
    } finally {
        Thread.currentThread().contextClassLoader = contextClassLoader
    }
}
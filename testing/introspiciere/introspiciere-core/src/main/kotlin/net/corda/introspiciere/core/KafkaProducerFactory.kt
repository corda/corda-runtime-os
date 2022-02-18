package net.corda.introspiciere.core

import net.corda.messagebus.kafka.serialization.CordaAvroSerializerImpl
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.reflections.Reflections
import java.net.InetAddress

class KafkaProducerFactory(val servers: List<String>) {

    private val classes: MutableSet<Class<out SpecificRecordBase>> by lazy {
        Reflections("net.corda").getSubTypesOf(SpecificRecordBase::class.java)
    }

    fun <V : Any> create(schemaClass: Class<V>): KafkaProducer<String, V> {
        val props = mapOf(
            ProducerConfig.CLIENT_ID_CONFIG to InetAddress.getLocalHost().hostName,
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to servers.joinToString(","),
            ProducerConfig.ACKS_CONFIG to "all"
        ).toProperties()

        return KafkaProducer(
            props,
            CordaAvroSerializerImpl(AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), String::class.java),
            CordaAvroSerializerImpl(AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), schemaClass)
        )
    }
}
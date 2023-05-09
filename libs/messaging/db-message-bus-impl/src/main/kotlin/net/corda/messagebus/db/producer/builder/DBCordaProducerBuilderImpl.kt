package net.corda.messagebus.db.producer.builder

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messagebus.db.configuration.MessageBusConfigResolver
import net.corda.messagebus.db.configuration.ResolvedProducerConfig
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.EntityManagerFactoryHolder
import net.corda.messagebus.db.producer.CordaAtomicDBProducerImpl
import net.corda.messagebus.db.producer.CordaTransactionalDBProducerImpl
import net.corda.messagebus.db.serialization.CordaDBAvroSerializerImpl
import net.corda.messagebus.db.serialization.MessageHeaderSerializerImpl
import net.corda.messagebus.db.util.WriteOffsets
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.schema.registry.AvroSchemaRegistry
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Builder for a DB Producer.
 */
@Suppress("Unused")
@Component(service = [CordaProducerBuilder::class])
class DBCordaProducerBuilderImpl @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
    @Reference(service = EntityManagerFactoryHolder::class)
    private val entityManagerFactoryHolder: EntityManagerFactoryHolder,
) : CordaProducerBuilder {

    private var writeOffsets: WriteOffsets? = null

    @Synchronized
    fun getWriteOffsets(resolvedConfig: ResolvedProducerConfig): WriteOffsets {
        if (writeOffsets == null) {
            val emf = entityManagerFactoryHolder.getEmf(
                resolvedConfig.jdbcUrl,
                resolvedConfig.jdbcUser,
                resolvedConfig.jdbcPass
            )
            writeOffsets = WriteOffsets(DBAccess(emf))
        }
        return writeOffsets ?: throw CordaMessageAPIFatalException("Write Offsets member should never be null.")
    }

    override fun createProducer(
        producerConfig: ProducerConfig,
        messageBusConfig: SmartConfig,
        onSerializationError: ((ByteArray) -> Unit)?
    ): CordaProducer {
        val isTransactional = producerConfig.transactional
        val resolver = MessageBusConfigResolver(messageBusConfig.factory)
        val resolvedConfig = resolver.resolve(messageBusConfig, producerConfig)
        val emf = entityManagerFactoryHolder.getEmf(
            resolvedConfig.jdbcUrl,
            resolvedConfig.jdbcUser,
            resolvedConfig.jdbcPass
        )

        return if (isTransactional) {
            CordaTransactionalDBProducerImpl(
                CordaDBAvroSerializerImpl(avroSchemaRegistry, onSerializationError),
                DBAccess(emf),
                getWriteOffsets(resolvedConfig),
                MessageHeaderSerializerImpl(),
                producerConfig.throwOnSerializationError
            )
        } else {
            CordaAtomicDBProducerImpl(
                CordaDBAvroSerializerImpl(avroSchemaRegistry, onSerializationError),
                DBAccess(emf),
                getWriteOffsets(resolvedConfig),
                MessageHeaderSerializerImpl(),
                producerConfig.throwOnSerializationError
            )
        }
    }
}

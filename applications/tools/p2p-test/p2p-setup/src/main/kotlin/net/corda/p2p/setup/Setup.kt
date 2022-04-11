package net.corda.p2p.setup

import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messagebus.kafka.consumer.builder.MessageBusConsumerBuilderImpl
import net.corda.messagebus.kafka.producer.builder.KafkaCordaProducerBuilderImpl
import net.corda.messagebus.kafka.serialization.CordaAvroSerializationFactoryImpl
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.publisher.factory.CordaPublisherFactory
import net.corda.messaging.subscription.consumer.builder.CordaConsumerBuilderImpl
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import net.corda.v5.base.util.contextLogger
import picocli.CommandLine
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    Setup(args).run()
}
class Setup(
    private val args: Array<String>,
) : Runnable {

    private val publisherFactory by lazy {
        val registry = AvroSchemaRegistryImpl()
        val serializationFactory = CordaAvroSerializationFactoryImpl(registry)
        val messageBusConsumerBuilder = MessageBusConsumerBuilderImpl(registry)
        val producerBuilder = KafkaCordaProducerBuilderImpl(registry)
        val consumerBuilder = CordaConsumerBuilderImpl(messageBusConsumerBuilder)
        val coordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
        CordaPublisherFactory(
            serializationFactory,
            producerBuilder,
            consumerBuilder,
            coordinatorFactory
        )
    }
    private companion object {
        private val logger = contextLogger()
    }

    override fun run() {
        val command = Command()
        val commandLine = CommandLine(command)
            .setCaseInsensitiveEnumValuesAllowed(true)
        @Suppress("SpreadOperator")
        val exitCode = commandLine.execute(*args)
        if (exitCode != 0) {
            exitProcess(exitCode)
        }

        val records = commandLine.parseResult.subcommands().mapNotNull {
            it.commandSpec().commandLine().getExecutionResult<Any?>() as? List<*>
        }.flatten()
            .filterIsInstance<Record<*, *>>()

        if (records.isNotEmpty()) {
            publisherFactory.createPublisher(
                PublisherConfig("p2p-setup"),
                command.nodeConfiguration(),
            ).use { publisher ->
                logger.info("Publishing ${records.size} records")
                publisher.publish(records).forEach {
                    it.join()
                }
                logger.info("Published ${records.size} records")
            }
        }
    }
}

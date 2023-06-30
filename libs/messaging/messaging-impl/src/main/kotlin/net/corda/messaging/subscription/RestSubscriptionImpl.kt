package net.corda.messaging.subscription

import io.javalin.Javalin
import io.javalin.core.util.Header
import io.javalin.http.Context
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.config.ResolvedSubscriptionConfig
import org.slf4j.LoggerFactory

class RestSubscriptionImpl<K: Any, V: Any>(
    private val processor: DurableProcessor<K, V>,
    private val cordaAvroSerializer: CordaAvroSerializer<Any>,
    private val cordaAvroDeserializer: CordaAvroDeserializer<Any>,
    private val config: ResolvedSubscriptionConfig,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : Subscription<K, V> {

    companion object {
        private val javalin = Javalin.create().start(8080)
    }

    private val logger = LoggerFactory.getLogger(config.clientId)
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(config.lifecycleCoordinatorName, ::lifecycleHandler)

    @Suppress("UNCHECKED_CAST")
    private fun process(context: Context) {
        context.header(Header.CACHE_CONTROL, "no-cache")
        try {
            val record = cordaAvroDeserializer.deserialize(context.bodyAsBytes()) as? Record<K, V>
                ?: throw IllegalArgumentException("Could not process record as body did not deserialize correctly.")
            val outputEvents = processor.onNext(listOf(record))
            // Assume for now that one input event == one output event, and therefore can just take the first one in the
            // list as the return type.
            val returnBody = cordaAvroSerializer.serialize(outputEvents.first())
                ?: throw IllegalArgumentException("Could not serialize output event to return")
            context.result(returnBody)
            context.status(200)
        } catch (e: Exception) {
            logger.warn("Failed to process REST event: ${e.message}", e)
            context.result("Failed to process message: ${e.message}")
            context.status(500)
        }
    }

    private fun lifecycleHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                javalin.get(config.topic, ::process)
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> {
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    override fun close() {
        lifecycleCoordinator.close()
    }

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name

    override fun start() {
        lifecycleCoordinator.postEvent(StartEvent())
    }

    override val isRunning: Boolean
        get() = true
}
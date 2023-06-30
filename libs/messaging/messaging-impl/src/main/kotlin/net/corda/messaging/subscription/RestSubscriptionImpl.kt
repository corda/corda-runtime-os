package net.corda.messaging.subscription

import io.javalin.Javalin
import io.javalin.core.util.Header
import io.javalin.http.Context
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.configuration.SmartConfig
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

class RestSubscriptionImpl<K: Any, V: Any>(
    private val processor: DurableProcessor<K, V>,
    private val cordaAvroSerializer: CordaAvroSerializer<Any>,
    private val cordaAvroDeserializer: CordaAvroDeserializer<Any>,
    private val config: ResolvedSubscriptionConfig,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : Subscription<K, V> {

    companion object {
        private val javalin = Javalin.create().start(System.getenv("MESSAGING_PORT").toInt())
    }

    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(config.lifecycleCoordinatorName, ::lifecycleHandler)

    private fun process(context: Context) {
        context.header(Header.CACHE_CONTROL, "no-cache")
        val record = cordaAvroDeserializer.deserialize(context.bodyAsBytes()) as? Record<K, V> ?: return
        val outputEvents = processor.onNext(listOf(record))
        // Assume for now that one input event == one output event, and therefore can just take the first one in the
        // list as the return type.
        val returnBody = cordaAvroSerializer.serialize(outputEvents.first()) ?: return
        context.result(returnBody)
        context.status(200)
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
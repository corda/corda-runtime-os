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
import net.corda.utilities.classload.executeWithThreadContextClassLoader
import net.corda.utilities.executeWithStdErrSuppressed
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleWiring
import org.slf4j.LoggerFactory

class RestSubscriptionImpl<V: Any>(
    private val processor: DurableProcessor<String, V>,
    private val cordaAvroSerializer: CordaAvroSerializer<Any>,
    private val cordaAvroDeserializer: CordaAvroDeserializer<Any>,
    private val config: ResolvedSubscriptionConfig,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : Subscription<String, V> {

    companion object {
        private val javalin = Javalin.create().also {
            startServer(it, 8080)
        }
        private fun startServer(server: Javalin, port: Int) {
            val bundle = FrameworkUtil.getBundle(WebSocketServletFactory::class.java)

            if (bundle == null) {
                server.start(port)
            } else {
                // We temporarily switch the context class loader to allow Javalin to find `WebSocketServletFactory`.
                executeWithThreadContextClassLoader(bundle.adapt(BundleWiring::class.java).classLoader) {
                    // Required because Javalin prints an error directly to stderr if it cannot find a logging
                    // implementation via standard class loading mechanism. This mechanism is not appropriate for OSGi.
                    // The logging implementation is found correctly in practice.
                    executeWithStdErrSuppressed {
                        server.start(port)
                    }
                }
            }
        }
    }

    private val logger = LoggerFactory.getLogger(config.clientId)
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(config.lifecycleCoordinatorName, ::lifecycleHandler)

    @Suppress("UNCHECKED_CAST")
    private fun process(context: Context) {
        context.header(Header.CACHE_CONTROL, "no-cache")
        try {
            val value = cordaAvroDeserializer.deserialize(context.bodyAsBytes()) as? V
                ?: throw IllegalArgumentException("Could not process record as body did not deserialize correctly.")
            val record = Record(config.topic, "foo", value)
            val outputEvents = processor.onNext(listOf(record))
            // Assume for now that one input event == one output event, and therefore can just take the first one in the
            // list as the return type.
            val returnBody = outputEvents.first().value?.let {
                cordaAvroSerializer.serialize(it)
            } ?: throw IllegalArgumentException("Failed to serialize output type")
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
                javalin.get("/", ::process)
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
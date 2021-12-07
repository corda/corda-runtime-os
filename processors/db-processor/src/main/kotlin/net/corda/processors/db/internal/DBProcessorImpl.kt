package net.corda.processors.db.internal

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.processors.db.DBProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** The processor for a `DBWorker`. */
@Suppress("Unused")
@Component(service = [DBProcessor::class])
class DBProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
) : DBProcessor {
    private companion object {
        val logger = contextLogger()
    }

    private var coordinator: LifecycleCoordinator? = null

    override fun start(instanceId: Int, config: SmartConfig) {
        logger.info("DB processor starting.")

        val eventHandler = DBEventHandler(subscriptionFactory, instanceId, config)
        // TODO - Joel - Not sure if naming the coordinator after the processor itself is an anti-pattern.
        coordinator = coordinatorFactory.createCoordinator<DBProcessor>(eventHandler).apply { start() }

        logger.info("jjj posting to coordinator")
        coordinator?.postEvent(object : LifecycleEvent {})
    }

    override fun stop() {
        logger.info("DB processor stopping.")
        coordinator?.stop()
    }
}

// TODO - Joel - Move to separate file.
private class DBEventHandler(
    subscriptionFactory: SubscriptionFactory,
    instanceId: Int,
    config: SmartConfig,
) : LifecycleEventHandler {

    private companion object {
        val logger = contextLogger()
        const val GROUP_NAME = "DB_EVENT_HANDLER"
    }

    private val subscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(GROUP_NAME, "config-update-request", instanceId), DBCompactedProcessor(), config
    )

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("jjj processing event of type $event in dbeventhandler")

        when (event) {
            is StartEvent -> subscription.start()
            is StopEvent -> subscription.stop()
        }
    }
}

// TODO - Joel - Move to separate file.
private class DBCompactedProcessor : CompactedProcessor<String, String> {
    private companion object {
        val logger = contextLogger()
    }

    override val keyClass = String::class.java
    override val valueClass = String::class.java

    override fun onSnapshot(currentData: Map<String, String>) {
        logger.info("jjj processing snapshot in dbcompactedprocessor: $currentData")
    }

    override fun onNext(newRecord: Record<String, String>, oldValue: String?, currentData: Map<String, String>) {
        logger.info("jjj processing update in dbcompactedprocessor: $newRecord")
        // TODO - Joel - Send config to DB.
        // TODO - Joel - Publish updated config.
    }
}
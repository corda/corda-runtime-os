package net.corda.processors.db.internal

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.processors.db.DBProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

// TODO - Joel - I'm currently hanging this all off the main processor. I'll want to create a sub-component instead.

/** The processor for a `DBWorker`. */
@Suppress("Unused")
@Component(service = [DBProcessor::class])
class DBProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
    @Reference(service = LiquibaseSchemaMigrator::class)
    private val schemaMigrator: LiquibaseSchemaMigrator
) : DBProcessor {
    private companion object {
        val logger = contextLogger()
    }

    private var coordinator: LifecycleCoordinator? = null

    override fun start(instanceId: Int, config: SmartConfig) {
        logger.info("DB processor starting.")

        val eventHandler = DBEventHandler(
            subscriptionFactory,
            publisherFactory,
            entityManagerFactoryFactory,
            schemaMigrator,
            instanceId,
            config
        )
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


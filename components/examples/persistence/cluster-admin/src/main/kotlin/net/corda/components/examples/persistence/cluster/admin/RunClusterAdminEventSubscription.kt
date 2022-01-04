package net.corda.components.examples.persistence.cluster.admin

import net.corda.components.examples.persistence.cluster.admin.processor.ClusterAdminEventProcessor
import net.corda.data.poc.persistence.ClusterAdminEvent
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import java.sql.Connection

@Component
@Suppress("LongParameterList")
class RunClusterAdminEventSubscription(
    private val subscriptionFactory: SubscriptionFactory,
    private var config: SmartConfig,
    private val instanceId: Int,
    private val dbConnection: Connection,
    private val schemaMigrator: LiquibaseSchemaMigrator,
    private val logger: Logger,
) : Lifecycle {
    private companion object {
        const val groupName = "clusterAdminEventsGroup"
        const val inputTopic = "cluster-admin-event"
    }

    private var subscription: Subscription<String, ClusterAdminEvent>? = null

    override val isRunning: Boolean
        get() = subscription?.isRunning ?: false

    override fun start() {
        if (!isRunning) {
            logger.info("Creating durable subscription for $inputTopic")
            val processor = ClusterAdminEventProcessor(dbConnection, schemaMigrator, logger)
            subscription = subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(groupName, inputTopic, instanceId),
                processor,
                config,
                null
            )

            logger.info("Starting durable subscription for $inputTopic")
            subscription?.start()
        }
    }

    override fun stop() {
        logger.info("Stopping durable sub for $inputTopic")
        subscription?.stop()
    }
}

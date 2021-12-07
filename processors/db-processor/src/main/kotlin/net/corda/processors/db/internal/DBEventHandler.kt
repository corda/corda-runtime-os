package net.corda.processors.db.internal

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.v5.base.util.contextLogger

internal class DBEventHandler(
    private val subscriptionFactory: SubscriptionFactory,
    private val publisherFactory: PublisherFactory,
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
    private val schemaMigrator: LiquibaseSchemaMigrator,
    private val instanceId: Int,
    private val config: SmartConfig,
) : LifecycleEventHandler {

    private companion object {
        val logger = contextLogger()
        const val GROUP_NAME = "DB_EVENT_HANDLER"
    }

    private val subscription by lazy {
        // TODO - Joel - Choose a client ID.
        val publisher = publisherFactory.createPublisher(PublisherConfig("joel", instanceId), config)

        val dbSource = PostgresDataSourceFactory().create(
            "jdbc:postgresql://cluster-db:5432/cordacluster",
            "user",
            "pass")
        val entityManagerFactory = entityManagerFactoryFactory.create(
            "joel",
            listOf(ConfigEntity::class.java),
            DbEntityManagerConfiguration(dbSource)
        )
        val entityManager = entityManagerFactory.createEntityManager()

        val dbChange = ClassloaderChangeLog(linkedSetOf(
            ClassloaderChangeLog.ChangeLogResourceFiles(
                ConfigEntity::class.java.packageName,
                listOf("migration/db.changelog-master.xml"),
                classLoader = ConfigEntity::class.java.classLoader)
        ))
        schemaMigrator.updateDb(dbSource.connection, dbChange, LiquibaseSchemaMigrator.PUBLIC_SCHEMA)

        subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(GROUP_NAME, "config-update-request", instanceId),
            DBCompactedProcessor(publisher, entityManager),
            config
        )
    }

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("jjj processing event of type $event in dbeventhandler")
        when (event) {
            is StartEvent -> subscription.start()
            is StopEvent -> subscription.stop()
        }
    }
}
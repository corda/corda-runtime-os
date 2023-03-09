package net.corda.virtualnode.write.db.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.virtualnode.write.db.VirtualNodeWriteService
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeWriterFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** An implementation of [VirtualNodeWriteService]. */
@Suppress("LongParameterList")
@Component(service = [VirtualNodeWriteService::class])
internal class VirtualNodeWriteServiceImpl @Activate constructor(
    @Reference(service = ConfigurationReadService::class)
    configReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    publisherFactory: PublisherFactory,
    @Reference(service = DbConnectionManager::class)
    dbConnectionManager: DbConnectionManager,
    @Reference(service = LiquibaseSchemaMigrator::class)
    schemaMigrator: LiquibaseSchemaMigrator,
    @Reference(service = GroupPolicyParser::class)
    private val groupPolicyParser: GroupPolicyParser,
) : VirtualNodeWriteService {
    private val coordinator = let {
        val vNodeWriterFactory = VirtualNodeWriterFactory(
            subscriptionFactory,
            publisherFactory,
            dbConnectionManager,
            VirtualNodesDbAdmin(dbConnectionManager),
            schemaMigrator,
            groupPolicyParser
        )
        val eventHandler = VirtualNodeWriteEventHandler(configReadService, vNodeWriterFactory)
        coordinatorFactory.createCoordinator<VirtualNodeWriteService>(eventHandler)
    }

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()
}
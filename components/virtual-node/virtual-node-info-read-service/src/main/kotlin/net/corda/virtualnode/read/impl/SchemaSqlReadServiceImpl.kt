package net.corda.virtualnode.read.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.core.ShortHash
import net.corda.data.virtualnode.VirtualNodeSchema
import net.corda.db.admin.DbChange
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.DbSchema
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpi.datamodel.repository.factory.CpiCpkRepositoryFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.utils.transaction
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.schema.Schemas
import net.corda.virtualnode.read.SchemaSqlReadService
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbChangeLog
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap

@Suppress("LongParameterList")
@Component(service = [SchemaSqlReadService::class])
class SchemaSqlReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = LiquibaseSchemaMigrator::class)
    private val schemaMigrator: LiquibaseSchemaMigrator,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
) : SchemaSqlReadService, CompactedProcessor<VirtualNodeSchema, String> {
    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var schemaSqlSubscription: CompactedSubscription<VirtualNodeSchema, String>? = null
    private val schemaSqlMap = ConcurrentHashMap<VirtualNodeSchema, String>()

    private var subReg: RegistrationHandle? = null
    private var publisher: Publisher? = null

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<SchemaSqlReadService>(::eventHandler)

    override val isRunning = true

    override fun start() = lifecycleCoordinator.start()

    override fun stop() = lifecycleCoordinator.stop()

    override val keyClass: Class<VirtualNodeSchema> get() = VirtualNodeSchema::class.java
    override val valueClass: Class<String> get() = String::class.java

    override fun initialise(config: SmartConfig) {
        subReg?.close()
        schemaSqlSubscription?.close()
        publisher?.close()
        publisher = publisherFactory.createPublisher(
            PublisherConfig("SCHEMA_SQL", true),
            config
        )

        schemaSqlSubscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(
                "Schema SQL Subscription",
                Schemas.VirtualNode.VIRTUAL_NODE_ASYNC_REQUEST_TOPIC
            ),
            this,
            config
        )

        subReg = lifecycleCoordinator.followStatusChangesByName(setOf(schemaSqlSubscription!!.subscriptionName))

        schemaSqlSubscription?.start()
    }

    override fun getSchemaSql(dbType: String, virtualNodeShortId: String?, cpiChecksum: String?): String {
        return schemaSqlMap[VirtualNodeSchema(dbType, virtualNodeShortId, cpiChecksum)].toString()
    }

    override fun onSnapshot(currentData: Map<VirtualNodeSchema, String>) {
        schemaSqlMap.clear()
        currentData.forEach { schemaSqlMap[it.key] = it.value }
    }

    override fun onNext(
        newRecord: Record<VirtualNodeSchema, String>,
        oldValue: String?,
        currentData: Map<VirtualNodeSchema, String>
    ) {
        try {
            val virtualNodeSchema = newRecord.key
            val sql = newRecord.value
            if (sql != null) {
                putValue(virtualNodeSchema)
            } else {
                schemaSqlMap.remove(virtualNodeSchema)
            }
        } catch (ex: Exception) {
            log.error("Unhandled error when processing onNext for schema SQL", ex)
        }
    }

    @Suppress("ThrowsCount")
    private fun putValue(virtualNodeSchema: VirtualNodeSchema) {
        val connection = dbConnectionManager.getClusterDataSource().connection
        val sql = when (virtualNodeSchema.dbType) {
            "crypto", "uniqueness" -> {
                val changelog = getChangelog(virtualNodeSchema.dbType)
                buildSqlWithStringWriter(connection, changelog)
            }

            "vault" -> (
                    {
                        if (virtualNodeSchema.virtualNodeShortHash == null && virtualNodeSchema.cpiChecksum != null) {
                            val changeLog = getChangelog(virtualNodeSchema.dbType)
                            val cpkChangeLog = getCpkChangelog(virtualNodeSchema.dbType)
                            buildSqlWithStringWriter(connection, changeLog) + buildSqlWithStringWriter(
                                connection,
                                cpkChangeLog
                            )
                        } else if (virtualNodeSchema.virtualNodeShortHash != null && virtualNodeSchema.cpiChecksum != null) {
                            val virtualNodeInfo = virtualNodeInfoReadService
                                .getByHoldingIdentityShortHash(
                                    ShortHash.parse(
                                        virtualNodeSchema.virtualNodeShortHash
                                    )
                                )
                                ?: throw ResourceNotFoundException(
                                    "Virtual node",
                                    virtualNodeSchema.virtualNodeShortHash
                                )
                            val connectionVNodeVault =
                                dbConnectionManager.createDatasource(virtualNodeInfo.vaultDdlConnectionId!!).connection
                            buildSqlWithStringWriter(
                                connectionVNodeVault,
                                getCpkChangelog(virtualNodeSchema.cpiChecksum)
                            )
                        } else {
                            throw IllegalArgumentException("Illegal argument combination for virtualNodeSchema")
                        }
                    }
                    ).toString()

            else -> throw IllegalArgumentException("Cannot use dbType that does not exist")
        }
        schemaSqlMap[virtualNodeSchema] = sql
    }

    private fun getChangelog(dbType: String): DbChange {
        val resourceSubPath = "vnode-$dbType"
        val schemaClass = DbSchema::class.java
        val fullName = "${schemaClass.packageName}.$resourceSubPath"
        val resourcePrefix = fullName.replace('.', '/')
        val changeLogFiles = ClassloaderChangeLog.ChangeLogResourceFiles(
            fullName,
            listOf("$resourcePrefix/db.changelog-master.xml"), // VirtualNodeDbType.VAULT.dbChangeFiles,
            classLoader = schemaClass.classLoader
        )
        return ClassloaderChangeLog(linkedSetOf(changeLogFiles))
    }

    private fun getCpkChangelog(cpiChecksum: String): DbChange {
        val cpkDbChangeLogRepository = CpiCpkRepositoryFactory().createCpkDbChangeLogRepository()
        dbConnectionManager.getClusterEntityManagerFactory().createEntityManager().transaction { em ->
            val cpiMetadata =
                CpiCpkRepositoryFactory().createCpiMetadataRepository().findByFileChecksum(em, cpiChecksum)
            val changelogsPerCpk = cpkDbChangeLogRepository.findByCpiId(em, cpiMetadata!!.cpiId)
            return VirtualNodeDbChangeLog(changelogsPerCpk)
        }
    }

    private fun buildSqlWithStringWriter(
        connection: Connection,
        dbChange: DbChange
    ): String {
        StringWriter().use { writer ->
            schemaMigrator.createUpdateSql(connection, dbChange, writer)
            return writer.toString()
        }
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
            }

            is RegistrationStatusChangeEvent -> {
                if (event.registration == subReg && event.status == LifecycleStatus.DOWN) {
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
        }
    }
}

package net.corda.virtualnode.write.db.impl.writer

import net.corda.data.ExceptionEnvelope
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.data.virtualnode.VirtualNodeCreateResponse
import net.corda.data.virtualnode.VirtualNodeDBResetRequest
import net.corda.data.virtualnode.VirtualNodeDBResetResponse
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.data.virtualnode.VirtualNodeStateChangeRequest
import net.corda.data.virtualnode.VirtualNodeStateChangeResponse
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.connection.manager.VirtualNodeDbType.CRYPTO
import net.corda.db.connection.manager.VirtualNodeDbType.UNIQUENESS
import net.corda.db.connection.manager.VirtualNodeDbType.VAULT
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DbPrivilege
import net.corda.db.core.DbPrivilege.DDL
import net.corda.db.core.DbPrivilege.DML
import net.corda.layeredpropertymap.toAvro
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.common.exception.VirtualNodeAlreadyExistsException
import net.corda.libs.virtualnode.datamodel.VirtualNodeNotFoundException
import net.corda.libs.virtualnode.datamodel.repository.HoldingIdentityRepository
import net.corda.libs.virtualnode.datamodel.repository.HoldingIdentityRepositoryImpl
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.Root.MGM_DEFAULT_GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC
import net.corda.utilities.debug
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import org.slf4j.LoggerFactory
import java.lang.System.currentTimeMillis
import java.time.Instant
import java.util.UUID
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManager
import javax.sql.DataSource
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.MigrationUtility
import kotlin.system.measureTimeMillis
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepository
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepositoryImpl

/**
 * An RPC responder processor that handles virtual node creation requests.
 *
 * For each virtual node creation request, persists the created virtual node to the cluster database publishes it to
 * Kafka.
 *
 * @property vnodePublisher Used to publish to Kafka.
 * @property oldVirtualNodeEntityRepository Used to retrieve and store virtual nodes and related entities.
 * @property vnodeDbFactory Used to construct a mapping object which holds the multiple database connections we have.
 * @property groupPolicyParser Parses group policy JSON strings and returns MemberInfo structures
 * @property clock A clock instance used to add timestamps to what the records we publish. This is configurable rather
 *           than always simply the system wall clock time so that we can control everything in tests.
 * @property changeLogsRepository Used to retrieve the changelogs for a CPI.
 */
@Suppress("LongParameterList", "TooManyFunctions", "LargeClass")
internal class VirtualNodeWriterProcessor(
    private val vnodePublisher: Publisher,
    private val dbConnectionManager: DbConnectionManager,
    private val oldVirtualNodeEntityRepository: VirtualNodeEntityRepository,
    private val vnodeDbFactory: VirtualNodeDbFactory,
    private val groupPolicyParser: GroupPolicyParser,
    private val clock: Clock,
    private val changeLogsRepository: CpkDbChangeLogRepository,
    private val holdingIdentityRepository: HoldingIdentityRepository = HoldingIdentityRepositoryImpl(),
    private val virtualNodeRepository: VirtualNodeRepository = VirtualNodeRepositoryImpl(),
    private val migrationUtility: MigrationUtility
) : RPCResponderProcessor<VirtualNodeManagementRequest, VirtualNodeManagementResponse> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val PUBLICATION_TIMEOUT_SECONDS = 30L
        val systemTerminatorTag = "${VAULT.name}-system-final"
        val cpkDbChangeLogRepository = CpkDbChangeLogRepositoryImpl()
    }

    @Suppress("ReturnCount", "ComplexMethod", "LongMethod")
    private fun createVirtualNode(
        instant: Instant,
        create: VirtualNodeCreateRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        // TODO - replace this with real metrics
        logger.info("Create new Virtual Node: ${create.x500Name} and ${create.cpiFileChecksum}")
        val startMillis = currentTimeMillis()

        measureTimeMillis {
            create.validationError()?.let { errMsg ->
                handleException(respFuture, IllegalArgumentException(errMsg))
                return
            }
        }.also {
            logger.debug {
                "[Create ${create.x500Name}] validation took $it ms, elapsed " +
                        "${currentTimeMillis() - startMillis} ms"
            }
        }


        try {
            val cpiMetadata: CpiMetadataLite?
            measureTimeMillis {
                cpiMetadata = oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(create.cpiFileChecksum)
                if (cpiMetadata == null) {
                    handleException(
                        respFuture,
                        CpiNotFoundException("CPI with file checksum ${create.cpiFileChecksum} was not found.")
                    )
                    return
                }
            }.also {
                logger.debug {
                    "[Create ${create.x500Name}] get metadata took $it ms, elapsed " +
                            "${currentTimeMillis() - startMillis} ms"
                }
            }

            // Generate group ID for MGM
            val groupId = cpiMetadata!!.mgmGroupId.let {
                if (it == MGM_DEFAULT_GROUP_ID) UUID.randomUUID().toString() else it
            }
            val holdingId = HoldingIdentity(MemberX500Name.parse(create.getX500CanonicalName()), groupId)
            measureTimeMillis {
                val emf = dbConnectionManager.getClusterEntityManagerFactory()
                emf.use { em ->
                    if (virtualNodeRepository.find(em, holdingId.shortHash) != null) {
                        handleException(
                            respFuture,
                            VirtualNodeAlreadyExistsException(
                                "Virtual node for CPI with file checksum ${create.cpiFileChecksum} and x500Name " +
                                        "${create.x500Name} already exists."
                            )
                        )
                        return
                    }

                    if (holdingIdentityRepository.find(em, holdingId.shortHash) != null) {
                        throw VirtualNodeWriteServiceException(
                            "New holding identity $holdingId has a short hash that collided with existing holding identity."
                        )
                    }
                }
            }.also {
                logger.debug {
                    "[Create ${create.x500Name}] validate holding ID took $it ms, elapsed " +
                            "${currentTimeMillis() - startMillis} ms"
                }
            }

            val vNodeDbs: Map<VirtualNodeDbType, VirtualNodeDb>
            measureTimeMillis {
                vNodeDbs = vnodeDbFactory.createVNodeDbs(holdingId.shortHash, create)
            }.also {
                logger.debug {
                    "[Create ${create.x500Name}] creating vnode DBs took $it ms, elapsed " +
                            "${currentTimeMillis() - startMillis} ms"
                }
            }

            measureTimeMillis {
                createSchemasAndUsers(holdingId, vNodeDbs.values)
            }.also {
                logger.debug {
                    "[Create ${create.x500Name}] creating vnode DB Schemas and users took $it ms, elapsed " +
                            "${currentTimeMillis() - startMillis} ms"
                }
            }

            measureTimeMillis {
                runDbMigrations(holdingId, vNodeDbs.values)
            }.also {
                logger.debug {
                    "[Create ${create.x500Name}] DB migrations took $it ms, elapsed " +
                            "${currentTimeMillis() - startMillis} ms"
                }
            }

            val vaultDb = vNodeDbs[VAULT]
            if (null == vaultDb) {
                handleException(respFuture, VirtualNodeWriteServiceException("Vault DB not configured"))
                return
            } else {
                measureTimeMillis {
                    runCpiMigrations(cpiMetadata, vaultDb, holdingId.shortHash.value)
                }.also {
                    logger.debug {
                        "[Create ${create.x500Name}] CPI DB migrations took $it ms, elapsed " +
                                "${currentTimeMillis() - startMillis} ms"
                    }
                }
            }

            val dbConnections: VirtualNodeDbConnections
            measureTimeMillis {
                dbConnections =
                    persistHoldingIdAndVirtualNode(holdingId, vNodeDbs, cpiMetadata.id, create.updateActor)
            }.also {
                logger.debug {
                    "[Create ${create.x500Name}] persisting VNode to DB took $it ms, elapsed " +
                            "${currentTimeMillis() - startMillis} ms"
                }
            }

            measureTimeMillis {
                publishVNodeInfo(holdingId, cpiMetadata, dbConnections)
            }.also {
                logger.debug {
                    "[Create ${create.x500Name}] persisting VNode Info to Kafka took $it ms, elapsed " +
                            "${currentTimeMillis() - startMillis} ms"
                }
            }

            measureTimeMillis {
                publishMgmInfo(holdingId, cpiMetadata.groupPolicy)
            }.also {
                logger.debug {
                    "[Create ${create.x500Name}] persisting Mgm Info to Kafka took $it ms, elapsed " +
                            "${currentTimeMillis() - startMillis} ms"
                }
            }

            measureTimeMillis {
                sendSuccessfulResponse(respFuture, instant, holdingId, cpiMetadata, dbConnections)
            }.also {
                logger.debug {
                    "[Create ${create.x500Name}] send response to RPC gateway took $it ms, elapsed " +
                            "${currentTimeMillis() - startMillis} ms"
                }
            }
        } catch (e: Exception) {
            handleException(respFuture, e)
        }
    }

    private fun resetVirtualNodeDb(
        instant: Instant,
        dbResetRequest: VirtualNodeDBResetRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        val em = dbConnectionManager.getClusterEntityManagerFactory().createEntityManager()
        val shortHashes = em.use {
            dbResetRequest.holdingIdentityShortHashes.map { currentVNodeShortHash ->
                val shortHash = ShortHash.Companion.of(currentVNodeShortHash)
                // Open a TX to find the connection information we need for the virtual nodes vault as it may live on
                //  another database.
                it.transaction { em ->
                    // Retrieve virtual node info
                    val virtualNodeInfo = virtualNodeRepository.find(em, shortHash)
                    if (null == virtualNodeInfo) {
                        logger.warn("Could not find the virtual node: $currentVNodeShortHash")
                        respFuture.complete(
                            VirtualNodeManagementResponse(
                                instant,
                                VirtualNodeManagementResponseFailure(
                                    ExceptionEnvelope(
                                        VirtualNodeNotFoundException::class.java.name,
                                        "Could not find the virtual node: $currentVNodeShortHash"
                                    )
                                )
                            )
                        )
                        return
                    }

                    val cpiMetadata = oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(
                        virtualNodeInfo.cpiIdentifier.name,
                        virtualNodeInfo.cpiIdentifier.version
                    )!!
                    dbConnectionManager.createDatasource(virtualNodeInfo.vaultDdlConnectionId!!).use { dataSource ->
                        // changelog tags are the CPK file checksum the changelog belongs to
                        val cpkChecksumsOfAppliedChangelogs: Set<String> = getAppliedChangelogTags(
                            em,
                            dataSource,
                            systemTerminatorTag
                        )

                        logger.info(
                            "CPK file checksums of currently applied changelogs on vault schema for virtual node " +
                                    "$currentVNodeShortHash: [${cpkChecksumsOfAppliedChangelogs.joinToString()}]"
                        )

                        val changesetsToRollback =
                            cpkDbChangeLogRepository.findByFileChecksum(em, cpkChecksumsOfAppliedChangelogs)
                                .groupBy { it.id.cpkFileChecksum }

                        changesetsToRollback.forEach { (cpkFileChecksum, changelogs) ->
                            logger.info(
                                "Virtual node '$currentVNodeShortHash' attempting to roll back the following changelogs for CPK " +
                                        "'$cpkFileChecksum' [${changelogs.joinToString { it.id.filePath }}]"
                            )
                            rollbackVirtualNodeDb(
                                dbConnectionManager.createDatasource(virtualNodeInfo.vaultDdlConnectionId!!),
                                changelogs,
                                systemTerminatorTag
                            )
                            logger.info("Migrations for CPK '$cpkFileChecksum' successfully rolled back.")
                        }
                    }

                    val changelogsToRun =
                        changeLogsRepository.findByCpiId(em, cpiMetadata.id).groupBy { it.id.cpkFileChecksum }

                    changelogsToRun.forEach { (cpkFileChecksum, changelogsForThisCpk) ->
                        try {
                            dbConnectionManager.createDatasource(virtualNodeInfo.vaultDdlConnectionId!!).use {
                                runCpkResyncMigrations(it, cpkFileChecksum, changelogsForThisCpk)
                            }
                        } catch (e: Exception) {
                            logger.warn(
                                "Error from liquibase API while running resync migrations for CPI ${cpiMetadata.id.name} - changelogs: [" +
                                        "${changelogsForThisCpk.joinToString { it.id.cpkFileChecksum + ", " + it.id.filePath }}]",
                                e
                            )
                            respFuture.complete(
                                VirtualNodeManagementResponse(
                                    instant,
                                    VirtualNodeManagementResponseFailure(
                                        ExceptionEnvelope(
                                            VirtualNodeWriteServiceException::class.java.name,
                                            e.message
                                        )
                                    )
                                )
                            )
                        }
                    }
                }
                shortHash.value
            }
        }

        respFuture.complete(
            VirtualNodeManagementResponse(
                instant,
                VirtualNodeDBResetResponse(shortHashes)
            )
        )
    }

    private fun rollbackVirtualNodeDb(
        dataSource: CloseableDataSource,
        changelogs: List<CpkDbChangeLog>,
        tagToRollbackTo: String
    ) {
        val dbChange = VirtualNodeDbChangeLog(changelogs)
        val connection = dataSource.connection
        LiquibaseSchemaMigratorImpl().rollBackDb(connection, dbChange, tagToRollbackTo)
    }

    // State change request produced by VirtualNodeMaintenanceRPCOpsImpl
    @Suppress("NestedBlockDepth", "ThrowsCount")
    private fun changeVirtualNodeState(
        instant: Instant,
        stateChangeRequest: VirtualNodeStateChangeRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {

        // Attempt and update, and on failure, pass the error back to the RPC processor
        try {
            val em = dbConnectionManager.getClusterEntityManagerFactory().createEntityManager()

            val updatedVirtualNode = em.use { entityManager ->

                val shortHash = ShortHash.Companion.of(stateChangeRequest.holdingIdentityShortHash)
                val nodeInfo = virtualNodeRepository.find(entityManager, shortHash)

                if (nodeInfo != null) {
                    val changelogsPerCpk = changeLogsRepository.findByCpiId(em, nodeInfo.cpiIdentifier)
                    if (stateChangeRequest.newState.lowercase(Locale.getDefault()) == "active") {
                        val inSync = migrationUtility.isVaultSchemaAndTargetCpiInSync(
                            stateChangeRequest.holdingIdentityShortHash, changelogsPerCpk, nodeInfo.vaultDmlConnectionId
                        )
                        if (!inSync) {
                            logger.info("Cannot set state to ACTIVE, db is not in sync with changelogs")
                            throw VirtualNodeDbException("Cannot set state to ACTIVE, db is not in sync with changelogs")
                        }
                    }
                } else {
                    throw VirtualNodeDbException("Unable to fetch node info")
                }

                virtualNodeRepository.updateVirtualNodeState(
                    entityManager,
                    stateChangeRequest.holdingIdentityShortHash,
                    stateChangeRequest.newState
                )
            }

            val avroPayload = updatedVirtualNode.toAvro()

            val virtualNodeRecord = Record(
                VIRTUAL_NODE_INFO_TOPIC,
                avroPayload.holdingIdentity,
                avroPayload
            )

            try {
                // TODO - CORE-3319 - Strategy for DB and Kafka retries.
                val future = vnodePublisher.publish(listOf(virtualNodeRecord)).first()

                // TODO - CORE-3730 - Define timeout policy.
                future.get()
            } catch (e: Exception) {
                throw VirtualNodeWriteServiceException(
                    "Record $virtualNodeRecord was written to the database, but couldn't be published. Cause: $e", e
                )
            }

            val response = VirtualNodeManagementResponse(
                instant,
                VirtualNodeStateChangeResponse(
                    stateChangeRequest.holdingIdentityShortHash,
                    VirtualNodeInfo.DEFAULT_INITIAL_STATE.name,
                    VirtualNodeInfo.DEFAULT_INITIAL_STATE.name,
                    VirtualNodeInfo.DEFAULT_INITIAL_STATE.name,
                    VirtualNodeInfo.DEFAULT_INITIAL_STATE.name
                )
            )
            respFuture.complete(response)
        } catch (e: Exception) {
            respFuture.complete(
                VirtualNodeManagementResponse(
                    instant,
                    VirtualNodeManagementResponseFailure(
                        ExceptionEnvelope(
                            e::class.java.name,
                            e.message
                        )
                    )
                )
            )
        }
    }

    /**
     * For each [request], the processor attempts to commit a new virtual node to the cluster database. If successful,
     * the created virtual node is then published by the [vnodePublisher] to the `VIRTUAL_NODE_INFO_TOPIC` topic.
     *
     * If both steps succeed, [respFuture] is completed successfully. Otherwise, it is completed unsuccessfully.
     */
    override fun onNext(
        request: VirtualNodeManagementRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        when (val typedRequest = request.request) {
            is VirtualNodeCreateRequest -> {
                logger.info("Handling virtual node creation request for ${typedRequest.x500Name}, ${typedRequest.cpiFileChecksum}")
                createVirtualNode(request.timestamp, typedRequest, respFuture)
            }
            is VirtualNodeStateChangeRequest -> {
                logger.info(
                    "Handling change virtual node state request for ${typedRequest.holdingIdentityShortHash} to ${typedRequest.newState}"
                )
                changeVirtualNodeState(request.timestamp, typedRequest, respFuture)
            }
            is VirtualNodeDBResetRequest -> {
                logger.info("Handling virtual node db reset request for ${typedRequest.holdingIdentityShortHashes.joinToString()}")
                resetVirtualNodeDb(request.timestamp, typedRequest, respFuture)
            }
            else -> throw VirtualNodeWriteServiceException("Unknown management request of type: ${typedRequest::class.java.name}")
        }
    }

    private fun VirtualNodeCreateRequest.validationError(): String? {
        if (cpiFileChecksum.isNullOrBlank()) {
            return "CPI file checksum value is missing"
        }

        if (!vaultDdlConnection.isNullOrBlank() && vaultDmlConnection.isNullOrBlank()) {
            return "If Vault DDL connection is provided, Vault DML connection needs to be provided as well."
        }

        if (!cryptoDdlConnection.isNullOrBlank() && cryptoDmlConnection.isNullOrBlank()) {
            return "If Crypto DDL connection is provided, Crypto DML connection needs to be provided as well."
        }

        if (!uniquenessDdlConnection.isNullOrBlank() && uniquenessDmlConnection.isNullOrBlank()) {
            return "If Uniqueness DDL connection is provided, Uniqueness DML connection needs to be provided as well."
        }

        try {
            MemberX500Name.parse(x500Name)
        } catch (e: Exception) {
            return "X500 name \"$x500Name\" could not be parsed. Cause: ${e.message}"
        }
        return null
    }

    private fun VirtualNodeCreateRequest.getX500CanonicalName(): String {
        // TODO replace toString with method that returns canonical name
        return MemberX500Name.parse(x500Name).toString()
    }

    private fun createSchemasAndUsers(holdingIdentity: HoldingIdentity, vNodeDbs: Collection<VirtualNodeDb>) {
        try {
            vNodeDbs.filter { it.isPlatformManagedDb }.forEach { it.createSchemasAndUsers() }
        } catch (e: Exception) {
            throw VirtualNodeWriteServiceException(
                "Error creating virtual node DB schemas and users for holding identity $holdingIdentity", e
            )
        }
    }

    private fun persistHoldingIdAndVirtualNode(
        holdingIdentity: HoldingIdentity,
        vNodeDbs: Map<VirtualNodeDbType, VirtualNodeDb>,
        cpiId: CpiIdentifier,
        updateActor: String
    ): VirtualNodeDbConnections {
        try {
            return dbConnectionManager.getClusterEntityManagerFactory().createEntityManager()
                .transaction { entityManager ->
                    val dbConnections =
                        VirtualNodeDbConnections(
                            putConnection(entityManager, vNodeDbs, VAULT, DDL, updateActor),
                            putConnection(entityManager, vNodeDbs, VAULT, DML, updateActor)!!,
                            putConnection(entityManager, vNodeDbs, CRYPTO, DDL, updateActor),
                            putConnection(entityManager, vNodeDbs, CRYPTO, DML, updateActor)!!,
                            putConnection(entityManager, vNodeDbs, UNIQUENESS, DDL, updateActor),
                            putConnection(entityManager, vNodeDbs, UNIQUENESS, DML, updateActor)!!,
                        )
                    holdingIdentityRepository.put(
                        entityManager,
                        holdingIdentity,
                    )
                    virtualNodeRepository.put(
                        entityManager,
                        holdingIdentity,
                        cpiId,
                        dbConnections.vaultDdlConnectionId,
                        dbConnections.vaultDmlConnectionId,
                        dbConnections.cryptoDdlConnectionId,
                        dbConnections.cryptoDmlConnectionId,
                        dbConnections.uniquenessDdlConnectionId,
                        dbConnections.uniquenessDmlConnectionId,
                    )
                    dbConnections
                }
        } catch (e: Exception) {
            throw VirtualNodeWriteServiceException(
                "Error persisting virtual node for holding identity $holdingIdentity",
                e
            )
        }
    }

    private fun putConnection(
        entityManager: EntityManager,
        vNodeDbs: Map<VirtualNodeDbType, VirtualNodeDb>,
        dbType: VirtualNodeDbType,
        dbPrivilege: DbPrivilege,
        updateActor: String
    ): UUID? {
        return vNodeDbs[dbType]?.let { vNodeDb ->
            vNodeDb.dbConnections[dbPrivilege]?.let { dbConnection ->
                with(dbConnection) {
                    dbConnectionManager.putConnection(
                        entityManager,
                        name,
                        dbPrivilege,
                        config,
                        description,
                        updateActor
                    )
                }
            }
        }
    }

    private fun runDbMigrations(holdingIdentity: HoldingIdentity, vNodeDbs: Collection<VirtualNodeDb>) {
        try {
            vNodeDbs.forEach { it.runDbMigration(systemTerminatorTag) }
        } catch (e: Exception) {
            throw VirtualNodeWriteServiceException(
                "Error running virtual node DB migration for holding identity $holdingIdentity",
                e
            )
        }
    }

    private fun runCpiMigrations(cpiMetadata: CpiMetadataLite, vaultDb: VirtualNodeDb, virtualNodeShortHash: String) {
        dbConnectionManager.getClusterEntityManagerFactory().createEntityManager().transaction { em ->
            // every changelog here is from a CPK currently associated with the given CPI.
            val changelogsPerCpk =
                changeLogsRepository.findByCpiId(em, cpiMetadata.id).groupBy { it.id.cpkFileChecksum }

            changelogsPerCpk.forEach { (cpkFileChecksum, changeLogs) ->
                logger.info("Preparing to run ${changeLogs.size} migrations for CPK '$cpkFileChecksum'.")
                val allChangeLogsForCpk = VirtualNodeDbChangeLog(changeLogs)
                try {
                    vaultDb.runCpiMigrations(allChangeLogsForCpk, cpkFileChecksum)
                } catch (e: Exception) {
                    val msg =
                        "CPI migrations failed for virtual node '$virtualNodeShortHash`. Failure occurred running CPI migrations on " +
                                "CPK with file checksum $cpkFileChecksum."
                    logger.warn(msg, e)
                    throw VirtualNodeWriteServiceException(msg, e)
                }
                logger.info("Successfully completed ${changeLogs.size} migrations for CPK with file checksum $cpkFileChecksum.")
            }
        }
    }

    private fun runCpkResyncMigrations(
        dataSource: CloseableDataSource,
        cpkFileChecksum: String,
        changelogs: List<CpkDbChangeLog>
    ) {
        if (changelogs.isEmpty()) return

        logger.info("Preparing to run ${changelogs.size} resync migrations for CPK '$cpkFileChecksum'.")

        LiquibaseSchemaMigratorImpl().updateDb(
            dataSource.connection,
            VirtualNodeDbChangeLog(changelogs.map {
                CpkDbChangeLog(
                    CpkDbChangeLogIdentifier(
                        it.id.cpkFileChecksum,
                        it.id.filePath
                    ), it.content
                )
            }),
            tag = cpkFileChecksum
        )
        logger.info("Resync migrations for CPK '$cpkFileChecksum' completed.")
    }

    private fun createVirtualNodeRecord(
        holdingIdentity: HoldingIdentity,
        cpiMetadata: CpiMetadataLite,
        dbConnections: VirtualNodeDbConnections
    ):
            Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo> {

        val cpiIdentifier = CpiIdentifier(cpiMetadata.id.name, cpiMetadata.id.version, cpiMetadata.id.signerSummaryHash)
        val virtualNodeInfo = with(dbConnections) {
            VirtualNodeInfo(
                holdingIdentity,
                cpiIdentifier,
                vaultDdlConnectionId,
                vaultDmlConnectionId,
                cryptoDdlConnectionId,
                cryptoDmlConnectionId,
                uniquenessDdlConnectionId,
                uniquenessDmlConnectionId,
                timestamp = clock.instant(),
            )
                .toAvro()
        }
        return Record(VIRTUAL_NODE_INFO_TOPIC, virtualNodeInfo.holdingIdentity, virtualNodeInfo)
    }

    private fun publishVNodeInfo(
        holdingIdentity: HoldingIdentity,
        cpiMetadata: CpiMetadataLite,
        dbConnections: VirtualNodeDbConnections
    ) {
        val virtualNodeRecord = createVirtualNodeRecord(holdingIdentity, cpiMetadata, dbConnections)
        try {
            // TODO - CORE-3319 - Strategy for DB and Kafka retries.
            val future = vnodePublisher.publish(listOf(virtualNodeRecord)).first()

            // TODO - CORE-3730 - Define timeout policy.
            future.get()
        } catch (e: Exception) {
            throw VirtualNodeWriteServiceException(
                "Record $virtualNodeRecord was written to the database, but couldn't be published. Cause: $e", e
            )
        }
    }

    private fun publishMgmInfo(holdingIdentity: HoldingIdentity, groupPolicyJson: String) {
        val mgmInfo = groupPolicyParser.run {
            getMgmInfo(holdingIdentity, groupPolicyJson)
        }
        if (mgmInfo == null) {
            logger.info("No MGM information found in group policy. MGM member info not published.")
            return
        }
        val mgmHoldingIdentity = HoldingIdentity(mgmInfo.name, mgmInfo.groupId)
        val mgmRecord = Record(
            MEMBER_LIST_TOPIC,
            "${holdingIdentity.shortHash}-${mgmHoldingIdentity.shortHash}",
            PersistentMemberInfo(
                holdingIdentity.toAvro(),
                mgmInfo.memberProvidedContext.toAvro(),
                mgmInfo.mgmProvidedContext.toAvro()
            )
        )
        try {
            vnodePublisher.publish(listOf(mgmRecord)).first().get(PUBLICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw VirtualNodeWriteServiceException(
                "MGM member info for Group ID: ${mgmInfo.groupId} could not be published. Cause: $e", e
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getAppliedChangelogTags(
        em: EntityManager,
        dataSource: DataSource,
        systemTerminatorTag: String
    ): Set<String> = (
            em.createNativeQuery(
                "SELECT tag FROM ${dataSource.connection.schema}.databasechangelog " +
                        "WHERE tag IS NOT NULL and tag != :systemTerminatorTag " +
                        "ORDER BY orderexecuted"
            )
                .setParameter("systemTerminatorTag", systemTerminatorTag)
                .resultList
                .toSet() as Set<String>
            ).toSet()

    private fun sendSuccessfulResponse(
        respFuture: CompletableFuture<VirtualNodeManagementResponse>,
        instant: Instant,
        holdingIdentity: HoldingIdentity,
        cpiMetadata: CpiMetadataLite,
        dbConnections: VirtualNodeDbConnections
    ) {
        val response = VirtualNodeManagementResponse(
            instant,
            VirtualNodeCreateResponse(
                holdingIdentity.x500Name.toString(), cpiMetadata.id.toAvro(), cpiMetadata.fileChecksum,
                holdingIdentity.groupId, holdingIdentity.toAvro(), holdingIdentity.shortHash.value,
                dbConnections.vaultDdlConnectionId?.toString(),
                dbConnections.vaultDmlConnectionId.toString(),
                dbConnections.cryptoDdlConnectionId?.toString(),
                dbConnections.cryptoDmlConnectionId.toString(),
                dbConnections.uniquenessDdlConnectionId?.toString(),
                dbConnections.uniquenessDmlConnectionId.toString(),
                null,
                VirtualNodeInfo.DEFAULT_INITIAL_STATE.name,
                VirtualNodeInfo.DEFAULT_INITIAL_STATE.name,
                VirtualNodeInfo.DEFAULT_INITIAL_STATE.name,
                VirtualNodeInfo.DEFAULT_INITIAL_STATE.name
            )
        )
        respFuture.complete(response)
    }

    /** Completes the [respFuture] with an [ExceptionEnvelope]. */
    @Suppress("LongParameterList")
    private fun handleException(
        respFuture: CompletableFuture<VirtualNodeManagementResponse>,
        e: Exception,
    ): Boolean {
        logger.error("Error while processing virtual node request: ${e.message}", e)
        val response = VirtualNodeManagementResponse(
            clock.instant(),
            VirtualNodeManagementResponseFailure(
                ExceptionEnvelope().apply {
                    errorType = e::class.java.name
                    errorMessage = e.message
                }
            )
        )
        return respFuture.complete(response)
    }
}

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
import net.corda.libs.cpi.datamodel.CpkDbChangeLogAuditEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.findDbChangeLogAuditForCpi
import net.corda.libs.cpi.datamodel.findDbChangeLogForCpi
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.common.exception.VirtualNodeAlreadyExistsException
import net.corda.libs.virtualnode.datamodel.repository.HoldingIdentityRepository
import net.corda.libs.virtualnode.datamodel.repository.HoldingIdentityRepositoryImpl
import net.corda.libs.virtualnode.datamodel.VirtualNodeNotFoundException
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
import net.corda.schema.Schemas.Membership.Companion.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_INFO_TOPIC
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.VirtualNodeState
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import java.lang.System.currentTimeMillis
import java.security.MessageDigest
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManager
import javax.sql.DataSource
import kotlin.system.measureTimeMillis

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
 * @property getChangelogs an overridable function to obtain the changelogs for a CPI. The default looks up in the database.
 *           Takes an EntityManager (since that lets us continue a transaction) and a CpiIdentifier as a parameter and
 *           returns a list of CpkDbChangeLogEntity.
 */
@Suppress("LongParameterList", "TooManyFunctions")
internal class VirtualNodeWriterProcessor(
    private val vnodePublisher: Publisher,
    private val dbConnectionManager: DbConnectionManager,
    private val oldVirtualNodeEntityRepository: VirtualNodeEntityRepository,
    private val vnodeDbFactory: VirtualNodeDbFactory,
    private val groupPolicyParser: GroupPolicyParser,
    private val clock: Clock,
    private val getChangelogs: (EntityManager, CpiIdentifier) -> List<CpkDbChangeLogEntity> = ::findDbChangeLogForCpi,
    private val holdingIdentityRepository: HoldingIdentityRepository = HoldingIdentityRepositoryImpl(),
    private val virtualNodeRepository: VirtualNodeRepository = VirtualNodeRepositoryImpl()
) : RPCResponderProcessor<VirtualNodeManagementRequest, VirtualNodeManagementResponse> {

    companion object {
        private val logger = contextLogger()
        const val PUBLICATION_TIMEOUT_SECONDS = 30L
        val systemTerminatorTag = "${VAULT.name}-system-final"

        private val mgmGroupPolicy = """
                {
                  "fileFormatVersion": 1,
                  "groupId": "CREATE_ID",
                  "registrationProtocol": "net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService",
                  "synchronisationProtocol": "net.corda.membership.impl.synchronisation.MgmSynchronisationServiceImpl"
                }
            """.trimIndent()

        private fun getMgmCpiShortHash(groupPolicy: String = mgmGroupPolicy) = ShortHash.of(
            with(DigestAlgorithmName.SHA2_256.name) {
                SecureHash(
                    this,
                    MessageDigest.getInstance(this).digest(groupPolicy.toByteArray())
                )
            }
        ).value
    }

    private fun VirtualNodeCreateRequest.isMgmCpiRequest(): Boolean {
        return "NO_CPI" == cpiFileChecksum
    }

    @Suppress("ReturnCount", "ComplexMethod")
    private fun createVirtualNode(
        instant: Instant,
        create: VirtualNodeCreateRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        val cpiShortHash = when {
            create.isMgmCpiRequest() -> getMgmCpiShortHash()
            else -> create.cpiFileChecksum
        }

        // TODO - replace this with real metrics
        logger.info("Create new Virtual Node: ${create.x500Name} and $cpiShortHash")
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
                cpiMetadata = oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(cpiShortHash)
                if (cpiMetadata == null) {
                    handleException(
                        respFuture,
                        CpiNotFoundException("CPI with file checksum $cpiShortHash was not found.")
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
                                "Virtual node for CPI with file checksum $cpiShortHash and x500Name " +
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
                    runCpiMigrations(cpiMetadata, vaultDb)
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
            dbResetRequest.holdingIdentityShortHashes.map { shortHashString ->
                val shortHash = ShortHash.Companion.of(shortHashString)
                // Open a TX to find the connection information we need for the virtual nodes vault as it may live on
                //  another database.
                it.transaction { tx ->
                    // Retrieve virtual node info
                    val virtualNodeInfo = virtualNodeRepository.find(tx, shortHash)
                    if(null == virtualNodeInfo) {
                            logger.warn("Could not find the virtual node: $shortHashString")
                            respFuture.complete(
                                VirtualNodeManagementResponse(
                                    instant,
                                    VirtualNodeManagementResponseFailure(
                                        ExceptionEnvelope(
                                            VirtualNodeNotFoundException::class.java.name,
                                            "Could not find the virtual node: $shortHashString"
                                        )
                                    )
                                )
                            )
                            return
                        }

                    // Retrieve CPI metadata
                    val cpiMetadata = oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(
                        virtualNodeInfo.cpiIdentifier.name,
                        virtualNodeInfo.cpiIdentifier.version
                    )!!
                    dbConnectionManager.createDatasource(virtualNodeInfo.vaultDdlConnectionId!!).use { dataSource ->
                        // Look up the tags(UUIDs) of the applied changelog entries
                        val appliedVersions: Set<UUID> = getAppliedVersions(
                            tx,
                            dataSource,
                            systemTerminatorTag
                        )
                        // Look up all audit entries that correspond to the UUID set that we just got
                        val migrationSet =
                            findDbChangeLogAuditForCpi(tx, virtualNodeInfo.cpiIdentifier, appliedVersions)
                        // Attempt to rollback the acquired changes
                        rollbackVirtualNodeDb(
                            dbConnectionManager.createDatasource(virtualNodeInfo.vaultDdlConnectionId!!),
                            migrationSet,
                            systemTerminatorTag
                        )
                    }
                    logger.info("Finished rolling back previous migrations, attempting to apply new ones")
                    // Attempt to apply the changes from the current CPI
                    val changelogs = getChangelogs(em, cpiMetadata.id)
                    dbConnectionManager.createDatasource(virtualNodeInfo.vaultDdlConnectionId!!).use { dataSource ->
                        runCpiResyncMigrations(
                            dataSource,
                            changelogs
                        )
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
        changelogs: List<CpkDbChangeLogAuditEntity>,
        tagToRollbackTo: String
    ) {
        val dbChange = VirtualNodeDbChangeLog(changelogs)
        val connection = dataSource.connection
        LiquibaseSchemaMigratorImpl().rollBackDb(connection, dbChange, tagToRollbackTo)
    }

    // State change request produced by VirtualNodeMaintenanceRPCOpsImpl
    private fun changeVirtualNodeState(
        instant: Instant,
        stateChangeRequest: VirtualNodeStateChangeRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {

        // Attempt and update, and on failure, pass the error back to the RPC processor
        try {
            val em = dbConnectionManager.getClusterEntityManagerFactory().createEntityManager()
            val updatedVirtualNode = em.use { entityManager ->
                virtualNodeRepository.updateVirtualNodeState(
                    entityManager,
                    stateChangeRequest.holdingIdentityShortHash,
                    VirtualNodeState.valueOf(stateChangeRequest.newState)
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
                    stateChangeRequest.newState
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
            is VirtualNodeCreateRequest -> createVirtualNode(request.timestamp, typedRequest, respFuture)
            is VirtualNodeStateChangeRequest -> changeVirtualNodeState(request.timestamp, typedRequest, respFuture)
            is VirtualNodeDBResetRequest -> resetVirtualNodeDb(request.timestamp, typedRequest, respFuture)
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
            vNodeDbs.filter { it.isClusterDb }.forEach { it.createSchemasAndUsers() }
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
                        dbConnections.vaultDdlConnectionId,
                        dbConnections.vaultDmlConnectionId,
                        dbConnections.cryptoDdlConnectionId,
                        dbConnections.cryptoDmlConnectionId,
                        dbConnections.uniquenessDdlConnectionId,
                        dbConnections.uniquenessDmlConnectionId,
                    )
                    virtualNodeRepository.put(entityManager, holdingIdentity, cpiId)
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

    private fun runCpiMigrations(cpiMetadata: CpiMetadataLite, vaultDb: VirtualNodeDb) =
    // we could potentially do one transaction per CPK; it seems more useful to blow up the
        // who migration if any CPK fails though, so that they can be iterative developed and repeated
        dbConnectionManager.getClusterEntityManagerFactory().createEntityManager().transaction {
            val changelogs = getChangelogs(it, cpiMetadata.id)
            changelogs.map { cl -> cl.id.cpkName }.distinct().sorted().forEach { cpkName ->
                val cpkChangelogs = changelogs.filter { cl2 -> cl2.id.cpkName == cpkName }
                logger.info("Doing ${cpkChangelogs.size} migrations for $cpkName")
                val dbChange = VirtualNodeDbChangeLog(cpkChangelogs)
                val changesetId = cpkChangelogs.first().changesetId
                try {
                    vaultDb.runCpiMigrations(dbChange, changesetId.toString())
                } catch (e: Exception) {
                    logger.error("Virtual node liquibase DB migration failure on CPK $cpkName with error $e")
                    throw VirtualNodeWriteServiceException(
                        "Error running virtual node DB migration for CPI liquibase migrations",
                        e
                    )
                }
                logger.info("Completed ${cpkChangelogs.size} migrations for $cpkName")
            }
        }

    private fun runCpiResyncMigrations(dataSource: CloseableDataSource, changelogs: List<CpkDbChangeLogEntity>) {
        changelogs.map { cl -> cl.id.cpkName }.distinct().sorted().forEach { cpkName ->
            val cpkChangelogs = changelogs.filter { cl2 -> cl2.id.cpkName == cpkName }
            val newChangeSetId = cpkChangelogs.first().changesetId
            logger.info("Applying change logs from $cpkName at $newChangeSetId")
            val connection = dataSource.connection
            LiquibaseSchemaMigratorImpl().updateDb(
                connection,
                VirtualNodeDbChangeLog(cpkChangelogs),
                tag = newChangeSetId.toString()
            )
        }
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
                state = VirtualNodeInfo.DEFAULT_INITIAL_STATE
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
    private fun getAppliedVersions(em: EntityManager, dataSource: DataSource, systemTerminatorTag: String): Set<UUID> =
        (
                em.createNativeQuery(
                    "SELECT tag FROM ${dataSource.connection.schema}.databasechangelog " +
                            "WHERE tag IS NOT NULL and tag != :systemTerminatorTag " +
                            "ORDER BY orderexecuted"
                )
                    .setParameter("systemTerminatorTag", systemTerminatorTag)
                    .resultList
                    .toSet() as Set<String>
                ).map { UUID.fromString(it) }.toSet()

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

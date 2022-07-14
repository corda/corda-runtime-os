package net.corda.virtualnode.write.db.impl.writer

import net.corda.data.ExceptionEnvelope
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.data.virtualnode.VirtualNodeCreateResponse
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.core.DbPrivilege.DDL
import net.corda.db.core.DbPrivilege.DML
import net.corda.layeredpropertymap.toAvro
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.findDbChangeLogForCpi
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.groupId
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.schema.Schemas.Membership.Companion.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_INFO_TOPIC
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbType.CRYPTO
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbType.VAULT
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManager

/**
 * An RPC responder processor that handles virtual node creation requests.
 *
 * For each virtual node creation request, persists the created virtual node to the cluster database publishes it to
 * Kafka.
 *
 * @property vnodePublisher Used to publish to Kafka.
 * @property virtualNodeEntityRepository Used to retrieve and store virtual nodes and related entities.
 * @property vnodeDbFactory Used to construct a mapping object which holds the multiple database connections we have.
 * @property groupPolicyParser Parses group policy JSON strings and returns MemberInfo structures
 * @property clock A clock instance used to add timestamps to what the records we publish. This is configurable rather
 *           than always simply the system wall clock time so that we can control everything in tests.
 * @property an overridable function to obtain the changelogs for a CPI. The default looks up in the database.
 *           Takes an EntityManager (since that lets us continue a transaction) and a CpiIdentifier as a parameter and
 *           returns a list of CpkDbChangeLogEntity.
 */
@Suppress("LongParameterList", "TooManyFunctions")
internal class VirtualNodeWriterProcessor(
    private val vnodePublisher: Publisher,
    private val dbConnectionManager: DbConnectionManager,
    private val virtualNodeEntityRepository: VirtualNodeEntityRepository,
    private val vnodeDbFactory: VirtualNodeDbFactory,
    private val groupPolicyParser: GroupPolicyParser,
    private val clock: Clock,
    private val getChangelogs: (EntityManager, CpiIdentifier) -> List<CpkDbChangeLogEntity> = ::findDbChangeLogForCpi
) : RPCResponderProcessor<VirtualNodeManagementRequest, VirtualNodeManagementResponse> {

    companion object {
        private val logger = contextLogger()
        const val PUBLICATION_TIMEOUT_SECONDS = 30L
    }

    @Suppress("ReturnCount")
    private fun createVirtualNode(
        instant: Instant,
        create: VirtualNodeCreateRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        create.validationError()?.let { errMsg ->
            handleException(respFuture, VirtualNodeWriteServiceException(errMsg))
            return
        }

        try {
            val cpiMetadata = virtualNodeEntityRepository.getCPIMetadata(create.cpiFileChecksum)
            if (cpiMetadata == null) {
                handleException(
                    respFuture,
                    VirtualNodeWriteServiceException("CPI with file checksum ${create.cpiFileChecksum} was not found.")
                )
                return
            }

            val holdingId = HoldingIdentity(create.getX500CanonicalName(), cpiMetadata.mgmGroupId)
            if (virtualNodeEntityRepository.virtualNodeExists(holdingId, cpiMetadata.id)) {
                handleException(
                    respFuture,
                    VirtualNodeWriteServiceException(
                        "Virtual node for CPI with file checksum ${create.cpiFileChecksum} and x500Name ${create.x500Name} already exists."
                    )
                )
                return
            }
            checkUniqueId(holdingId)

            val vNodeDbs = vnodeDbFactory.createVNodeDbs(holdingId.id, create)

            createSchemasAndUsers(holdingId, vNodeDbs.values)

            runDbMigrations(holdingId, vNodeDbs.values)

            val vaultDb = vNodeDbs[VAULT]
            if ( null == vaultDb) {
                handleException(respFuture, VirtualNodeWriteServiceException("Vault DB not configured"))
                return
            } else {
                runCpiMigrations(cpiMetadata, vaultDb)
            }

            val dbConnections = persistHoldingIdAndVirtualNode(holdingId, vNodeDbs, cpiMetadata.id, create.updateActor)

            publishVNodeInfo(holdingId, cpiMetadata, dbConnections)

            publishMgmInfo(holdingId, cpiMetadata.groupPolicy)

            sendSuccessfulResponse(respFuture, instant, holdingId, cpiMetadata, dbConnections)
        } catch (e: Exception) {
            handleException(respFuture, e)
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
            else -> throw VirtualNodeWriteServiceException("Unknown management request of type: ${typedRequest::class.java.name}")
        }
    }

    private fun VirtualNodeCreateRequest.validationError(): String? {
        if (!vaultDdlConnection.isNullOrBlank() && vaultDmlConnection.isNullOrBlank()) {
            return "If Vault DDL connection is provided, Vault DML connection needs to be provided as well."
        }

        if (!cryptoDdlConnection.isNullOrBlank() && cryptoDmlConnection.isNullOrBlank()) {
            return "If Crypto DDL connection is provided, Crypto DML connection needs to be provided as well."
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

    private fun checkUniqueId(holdingId: HoldingIdentity) {
        virtualNodeEntityRepository.getHoldingIdentity(holdingId.id)?.let { storedHoldingId ->
            if (storedHoldingId != holdingId) {
                throw VirtualNodeWriteServiceException(
                    "New holding identity $holdingId has a short hash that collided with existing holding identity $storedHoldingId."
                )
            }
        }
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
                            putConnection(entityManager, vNodeDbs, CRYPTO, DML, updateActor)!!
                        )
                    virtualNodeEntityRepository.putHoldingIdentity(entityManager, holdingIdentity, dbConnections)
                    virtualNodeEntityRepository.putVirtualNode(entityManager, holdingIdentity, cpiId)
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
            vNodeDbs.forEach { it.runDbMigration() }
        } catch (e: Exception) {
            throw VirtualNodeWriteServiceException(
                "Error running virtual node DB migration for holding identity $holdingIdentity",
                e
            )
        }
    }

    private fun runCpiMigrations(cpiMetadata: CpiMetadataLite, vaultDb: VirtualNodeDb) {
        dbConnectionManager.getClusterEntityManagerFactory().createEntityManager().transaction {
            val changelogs = getChangelogs(it, cpiMetadata.id)
            logger.info("Found ${changelogs.size} changelogs for ${cpiMetadata.id.name}:${cpiMetadata.id.version}")
            if (changelogs.isEmpty()) return
            val dbChange = VirtualNodeDbChangeLog(changelogs)
            try {
                vaultDb.runCpiMigrations(dbChange)
            } catch (e: Exception) {
                throw VirtualNodeWriteServiceException(
                    "Error running virtual node DB migration for CPI liquibase migrations",
                    e
                )
            }
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
                timestamp = clock.instant()
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
        val mgmHoldingIdentity = HoldingIdentity(mgmInfo.name.toString(), mgmInfo.groupId)
        val mgmRecord = Record(
            MEMBER_LIST_TOPIC,
            "${holdingIdentity.id}-${mgmHoldingIdentity.id}",
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
                holdingIdentity.x500Name, cpiMetadata.id.toAvro(), cpiMetadata.fileChecksum,
                holdingIdentity.groupId, holdingIdentity.toAvro(), holdingIdentity.id,
                dbConnections.vaultDdlConnectionId?.toString(),
                dbConnections.vaultDmlConnectionId.toString(),
                dbConnections.cryptoDdlConnectionId?.toString(),
                dbConnections.cryptoDmlConnectionId.toString(),
                null
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

package net.corda.virtualnode.write.db.impl.writer

import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.SecureHash
import net.corda.data.packaging.CPIIdentifier
import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.db.core.DbPrivilege
import net.corda.db.core.DbPrivilege.*
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.packaging.CPI
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_INFO_TOPIC
import net.corda.v5.membership.identity.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbType.*
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * An RPC responder processor that handles virtual node creation requests.
 *
 * For each virtual node creation request, persists the created virtual node to the cluster database publishes it to
 * Kafka.
 *
 * @property vnodePublisher Used to publish to Kafka.
 * @property virtualNodeEntityRepository Used to retrieve and store virtual nodes and related entities.
 */
internal class VirtualNodeWriterProcessor(
    private val vnodePublisher: Publisher,
    private val virtualNodeEntityRepository: VirtualNodeEntityRepository,
    private val vnodeDbFactory: VirtualNodeDbFactory
) : RPCResponderProcessor<VirtualNodeCreationRequest, VirtualNodeCreationResponse> {

    /**
     * For each [request], the processor attempts to commit a new virtual node to the cluster database. If successful,
     * the created virtual node is then published by the [vnodePublisher] to the `VIRTUAL_NODE_INFO_TOPIC` topic.
     *
     * If both steps succeed, [respFuture] is completed successfully. Otherwise, it is completed unsuccessfully.
     */
    override fun onNext(
        request: VirtualNodeCreationRequest,
        respFuture: CompletableFuture<VirtualNodeCreationResponse>
    ) {
        var cpiMetadata: CPIMetadata? = null
        var holdingId: HoldingIdentity? = null
        try {
            request.validationError()?.let { errMsg ->
                handleException(respFuture, errMsg)
                return
            }

            cpiMetadata = virtualNodeEntityRepository.getCPIMetadata(request.cpiIdHash)
            if (cpiMetadata == null) {
                handleException(respFuture, "CPI with hash ${request.cpiIdHash} was not found.")
                return
            }

            // Save or update Holding Identity
            // TODO replace toString with method that returns canonical name
            val x500CanonicalName = MemberX500Name.parse(request.x500Name).toString()
            holdingId = HoldingIdentity(x500CanonicalName, cpiMetadata.mgmGroupId)
            checkUniqueId(holdingId)

            // Persist holding identity and virtual node instance
            virtualNodeEntityRepository.putHoldingIdentity(holdingId)
            virtualNodeEntityRepository.putVirtualNode(holdingId, cpiMetadata.id)

            // Create connection details
            val vNodeDbs = vnodeDbFactory.createVNodeDbs(holdingId.id, request)

            createSchemasAndUsers(holdingId, vNodeDbs.values)

            insertConnections(holdingId, vNodeDbs, request.updateActor)

            runDbMigrations(holdingId, vNodeDbs.values)

            // Publish VNode info
            val virtualNodeRecord = createVirtualNodeRecord(holdingId, cpiMetadata)
            publishVNodeInfo(virtualNodeRecord)

            sendSuccessfulResponse(respFuture, request, holdingId, cpiMetadata)
        } catch (e: Exception) {
            handleException(respFuture, e, cpiMetadata, holdingId)
        }
    }

    private fun VirtualNodeCreationRequest.validationError(): String? {
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

    private fun checkUniqueId(holdingId: HoldingIdentity) {
        virtualNodeEntityRepository.getHoldingIdentity(holdingId.id)?.let { storedHoldingId ->
            if (storedHoldingId != holdingId) {
                throw VirtualNodeWriteServiceException("New holding identity $holdingId has a short hash that collided with existing holding identity $storedHoldingId.")
            }
        }
    }

    private fun createSchemasAndUsers(holdingIdentity: HoldingIdentity, vNodeDbs: Collection<VirtualNodeDb>) {
        try {
            vNodeDbs.filter { it.isClusterDb }.forEach { it.createSchemasAndUsers() }
        } catch (e: Exception) {
            throw VirtualNodeWriteServiceException("Error creating virtual node DB schemas and users for holding identity $holdingIdentity", e)
        }
    }

    private fun insertConnections(holdingIdentity: HoldingIdentity, vNodeDbs: Map<VirtualNodeDbType, VirtualNodeDb>, updateActor: String) {
        try {
            with(holdingIdentity) {
                vaultDdlConnectionId = putConnection(vNodeDbs, VAULT, DDL, updateActor)
                vaultDmlConnectionId = putConnection(vNodeDbs, VAULT, DML, updateActor)
                cryptoDdlConnectionId = putConnection(vNodeDbs, CRYPTO, DDL, updateActor)
                cryptoDmlConnectionId = putConnection(vNodeDbs, CRYPTO, DML, updateActor)
            }
            virtualNodeEntityRepository.putHoldingIdentity(holdingIdentity)
        } catch (e: Exception) {
            throw VirtualNodeWriteServiceException("Error persisting virtual node DB connections for holding identity $holdingIdentity", e)
        }
    }

    private fun putConnection(vNodeDbs: Map<VirtualNodeDbType, VirtualNodeDb>, dbType: VirtualNodeDbType, dbPrivilege: DbPrivilege, updateActor: String): UUID? {
        return vNodeDbs[dbType]?.let {  vNodeDb ->
            vNodeDb.dbConnections[dbPrivilege]?.let { dbConnection ->
                with (dbConnection) {
                    virtualNodeEntityRepository.putVirtualNodeConnection(name, dbPrivilege, config, description, updateActor)
                }
            }
        }
    }

    private fun runDbMigrations(holdingIdentity: HoldingIdentity, vNodeDbs: Collection<VirtualNodeDb>) {
        try {
            vNodeDbs.forEach{ it.runDbMigration() }
        } catch (e: Exception) {
            throw VirtualNodeWriteServiceException("Error running virtual node DB migration for holding identity $holdingIdentity", e)
        }
    }

    private fun createVirtualNodeRecord(holdingIdentity: HoldingIdentity, cpiMetadata: CPIMetadata):
            Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo> {
        val virtualNodeInfo = VirtualNodeInfo(holdingIdentity, cpiMetadata.id).toAvro()
        return Record(VIRTUAL_NODE_INFO_TOPIC, virtualNodeInfo.holdingIdentity, virtualNodeInfo)
    }

    private fun publishVNodeInfo(virtualNodeRecord: Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>) {
        try {
            // TODO - CORE-3319 - Strategy for DB and Kafka retries.
            val future = vnodePublisher.publish(listOf(virtualNodeRecord)).first()

            // TODO - CORE-3730 - Define timeout policy.
            future.get()
        } catch (e: Exception) {
            throw VirtualNodeWriteServiceException("Record $virtualNodeRecord was written to the database, but couldn't be published. Cause: $e", e)
        }
    }

    private fun sendSuccessfulResponse(
        respFuture: CompletableFuture<VirtualNodeCreationResponse>,
        request: VirtualNodeCreationRequest,
        holdingIdentity: HoldingIdentity,
        cpiMetadata: CPIMetadata) {
        val response = VirtualNodeCreationResponse(
            true,
            null,
            request.x500Name,
            cpiMetadata.id.toAvro(),
            request.cpiIdHash,
            cpiMetadata.mgmGroupId,
            holdingIdentity.toAvro(),
            holdingIdentity.id
        )
        respFuture.complete(response)
    }

    /** Completes the [respFuture] with an [ExceptionEnvelope]. */
    @Suppress("LongParameterList")
    private fun handleException(
        respFuture: CompletableFuture<VirtualNodeCreationResponse>,
        errMsg: String,
        errClassName: String = VirtualNodeWriteServiceException::class.java.name,
        cpiMetadata: CPIMetadata? = null,
        holdingId: HoldingIdentity? = null
    ): Boolean {
        val exception = ExceptionEnvelope(errClassName, errMsg)
        val response = VirtualNodeCreationResponse(
            false,
            exception,
            holdingId?.x500Name,
            cpiMetadata?.id?.toAvro(),
            cpiMetadata?.idShortHash,
            holdingId?.groupId,
            holdingId?.toAvro(),
            holdingId?.id
        )
        return respFuture.complete(response)
    }

    private fun handleException(
        respFuture: CompletableFuture<VirtualNodeCreationResponse>,
        exception: Exception,
        cpiMetadata: CPIMetadata? = null,
        holdingId: HoldingIdentity? = null
    ): Boolean {
        val errMsg = if (exception.cause != null) "${exception.message} Cause: ${exception.cause}" else exception.message ?: ""
        return handleException(respFuture, errMsg, VirtualNodeWriteServiceException::class.java.name, cpiMetadata, holdingId)
    }

    /** Converts a [CPI.Identifier] to its Avro representation. */
    private fun CPI.Identifier.toAvro(): CPIIdentifier {
        val secureHashAvro = SecureHash(signerSummaryHash?.algorithm, ByteBuffer.wrap(signerSummaryHash?.bytes))
        return CPIIdentifier(name, version, secureHashAvro)
    }
}
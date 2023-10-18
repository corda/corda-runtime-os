package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import java.time.Instant
import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.data.virtualnode.VirtualNodeOperationStatus
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.libs.external.messaging.ExternalMessagingRouteConfigGenerator
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationStateDto
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.VirtualNode.VIRTUAL_NODE_OPERATION_STATUS_TOPIC
import net.corda.tracing.trace
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.toCorda
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbFactory
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeWriterProcessor
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.VirtualNodeAsyncOperationHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.factories.RecordFactory
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.services.CreateVirtualNodeService
import org.slf4j.Logger

@Suppress("LongParameterList")
internal class CreateVirtualNodeOperationHandler(
    private val createVirtualNodeService: CreateVirtualNodeService,
    private val virtualNodeDbFactory: VirtualNodeDbFactory,
    private val recordFactory: RecordFactory,
    private val policyParser: GroupPolicyParser,
    private val statusPublisher: Publisher,
    private val externalMessagingRouteConfigGenerator: ExternalMessagingRouteConfigGenerator,
    private val logger: Logger
) : VirtualNodeAsyncOperationHandler<VirtualNodeCreateRequest> {

    override fun handle(
        requestTimestamp: Instant,
        requestId: String,
        request: VirtualNodeCreateRequest
    ) {

        publishStartProcessingStatus(requestId)

        try {
            val holdingId = request.holdingId.toCorda()
            val x500Name = holdingId.x500Name.toString()

            logger.info("Create new Virtual Node: $x500Name and ${request.cpiFileChecksum}")
            val execLog = ExecutionTimeLogger(x500Name, requestTimestamp.toEpochMilli(), logger)

            val requestValidationResult = execLog.measureExecTime("validation") {
                createVirtualNodeService.validateRequest(request)
            }

            if (requestValidationResult != null) {
                throw IllegalArgumentException(requestValidationResult)
            }

            val cpiMetadata = execLog.measureExecTime("get CPI metadata") {
                createVirtualNodeService.getCpiMetaData(request.cpiFileChecksum)
            }

            execLog.measureExecTime("check holding identity uniqueness") {
                createVirtualNodeService.ensureHoldingIdentityIsUnique(request)
            }

            val vNodeDbs = execLog.measureExecTime("get virtual node databases") {
                virtualNodeDbFactory.createVNodeDbs(holdingId.shortHash, request)
            }

            // For each of the platform DB's run the creation process
            for (vNodeDb in vNodeDbs.values.filter { it.isPlatformManagedDb }) {
                execLog.measureExecTime("create schema and user in ${vNodeDb.dbType} DB") {
                    vNodeDb.createSchemasAndUsers()
                }
            }

            for (vNodeDb in vNodeDbs.values.filter { it.isPlatformManagedDb || it.ddlConnectionProvided }) {
                execLog.measureExecTime("apply DB migrations in ${vNodeDb.dbType} DB") {
                    vNodeDb.runDbMigration(VirtualNodeWriterProcessor.systemTerminatorTag)
                }

                if (vNodeDb.dbType == VirtualNodeDbType.VAULT) {
                    execLog.measureExecTime("apply CPI migrations in ${vNodeDb.dbType} DB") {
                        createVirtualNodeService.runCpiMigrations(
                            cpiMetadata,
                            vNodeDb,
                            holdingId
                        )
                    }
                }
            }

            val externalMessagingRouteConfig = externalMessagingRouteConfigGenerator.generateNewConfig(
                holdingId,
                cpiMetadata.cpiId,
                cpiMetadata.cpksMetadata
            )
            
            logger.info("Generated new ExternalMessagingRouteConfig as: $externalMessagingRouteConfig")

            val vNodeConnections = execLog.measureExecTime("persist holding ID and virtual node") {
                createVirtualNodeService.persistHoldingIdAndVirtualNode(
                    holdingId,
                    vNodeDbs,
                    cpiMetadata.cpiId,
                    request.updateActor,
                    externalMessagingRouteConfig
                )
            }

            val mgmInfo = if (!GroupPolicyParser.isStaticNetwork(cpiMetadata.groupPolicy!!)) {
                policyParser.getMgmInfo(holdingId, cpiMetadata.groupPolicy!!)
            } else {
                null
            }

            val records = if (mgmInfo == null) {
                logger.info(".No MGM information found in group policy. MGM member info not published.")
                mutableListOf()
            } else {
                mutableListOf(recordFactory.createMgmInfoRecord(holdingId, mgmInfo))
            }

            records.add(
                recordFactory.createVirtualNodeInfoRecord(
                    holdingId,
                    cpiMetadata.cpiId,
                    vNodeConnections,
                    externalMessagingRouteConfig
                )
            )

            execLog.measureExecTime("publish virtual node and MGM info") {
                createVirtualNodeService.publishRecords(records)
            }
        } catch (e: Exception) {
            publishErrorStatus(requestId, e.message ?: "Unexpected error")
            throw e
        }

        publishProcessingCompletedStatus(requestId)
    }

    private fun publishStartProcessingStatus(requestId: String) {
        publishStatusMessage(requestId, getAvroStatusObject(requestId, VirtualNodeOperationStateDto.IN_PROGRESS))
    }

    private fun publishProcessingCompletedStatus(requestId: String) {
        publishStatusMessage(requestId, getAvroStatusObject(requestId, VirtualNodeOperationStateDto.COMPLETED))
    }

    private fun publishErrorStatus(requestId: String, reason: String) {
        val message = getAvroStatusObject(requestId, VirtualNodeOperationStateDto.UNEXPECTED_FAILURE)
        message.errors = reason
        publishStatusMessage(requestId, message)
    }

    private fun publishStatusMessage(requestId: String, message: VirtualNodeOperationStatus) {
        try {
            statusPublisher.publish(
                listOf(
                    Record(
                        VIRTUAL_NODE_OPERATION_STATUS_TOPIC,
                        requestId,
                        message
                    )
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to publish status update to Kafka for request ID = '$requestId'", e)
        }
    }

    private fun getAvroStatusObject(
        requestId: String,
        status: VirtualNodeOperationStateDto
    ): VirtualNodeOperationStatus {
        val now = Instant.now()
        return VirtualNodeOperationStatus.newBuilder()
            .setRequestId(requestId)
            .setRequestData("{}")
            .setRequestTimestamp(now)
            .setLatestUpdateTimestamp(now)
            .setHeartbeatTimestamp(null)
            .setState(status.name)
            .setErrors(null)
            .build()
    }

    class ExecutionTimeLogger(
        private val vNodeName: String,
        private val creationTime: Long,
        private val logger: Logger,
        private val clock: Clock = UTCClock()
    ) {
        fun <T> measureExecTime(stage: String, call: () -> T): T {
            return trace(stage) {
                val start = clock.instant().toEpochMilli()
                val result = call()
                val end = clock.instant().toEpochMilli()
                logger.debug("[Create ${vNodeName}] ${stage} took ${end - start}ms, elapsed ${end - creationTime}ms")
                result
            }
        }
    }
}
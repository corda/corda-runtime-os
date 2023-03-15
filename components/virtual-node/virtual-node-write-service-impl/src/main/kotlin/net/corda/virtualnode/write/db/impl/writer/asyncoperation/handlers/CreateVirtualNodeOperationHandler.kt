package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.toCorda
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbFactory
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeWriterProcessor
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.VirtualNodeAsyncOperationHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.factories.RecordFactory
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.services.CreateVirtualNodeService
import org.slf4j.Logger
import java.time.Instant

internal class CreateVirtualNodeOperationHandler(
    private val createVirtualNodeService: CreateVirtualNodeService,
    private val virtualNodeDbFactory: VirtualNodeDbFactory,
    private val recordFactory: RecordFactory,
    private val policyParser: GroupPolicyParser,
    private val logger: Logger
) : VirtualNodeAsyncOperationHandler<VirtualNodeCreateRequest> {

    override fun handle(
        requestTimestamp: Instant,
        requestId: String,
        request: VirtualNodeCreateRequest
    ) {
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
            virtualNodeDbFactory.createVNodeDbs(holdingId.shortHash,request)
        }

        // For each of the platform DB's run the creation process
        for (vNodeDb in vNodeDbs.values.filter { it.isPlatformManagedDb }) {
            execLog.measureExecTime("create schema and user in ${vNodeDb.dbType} DB") {
                vNodeDb.createSchemasAndUsers()
            }

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

        val vNodeConnections = execLog.measureExecTime("persist holding ID and virtual node") {
            createVirtualNodeService.persistHoldingIdAndVirtualNode(
                holdingId,
                vNodeDbs,
                cpiMetadata.id,
                request.updateActor
            )
        }

        val mgmInfo = if(!GroupPolicyParser.isStaticNetwork(cpiMetadata.groupPolicy)) {
            policyParser.getMgmInfo(holdingId, cpiMetadata.groupPolicy)
        } else {
            null
        }

        val records = if (mgmInfo == null) {
            logger.info(".No MGM information found in group policy. MGM member info not published.")
            mutableListOf()
        }else{
            mutableListOf(recordFactory.createMgmInfoRecord(holdingId, mgmInfo))
        }

        records.add(recordFactory.createVirtualNodeInfoRecord(holdingId, cpiMetadata.id, vNodeConnections))

        execLog.measureExecTime("publish virtual node and MGM info") {
            createVirtualNodeService.publishRecords(records)
        }
    }

    class ExecutionTimeLogger(
        private val vNodeName: String,
        private val creationTime: Long,
        private val logger: Logger,
        private val clock: Clock = UTCClock()
    ) {
        fun <T> measureExecTime(stage: String, call: () -> T): T {
            val start = clock.instant().toEpochMilli()
            val result = call()
            val end = clock.instant().toEpochMilli()
            logger.debug("[Create ${vNodeName}] ${stage} took ${end-start}ms, elapsed ${end - creationTime}ms")
            return result
        }
    }
}
package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import net.corda.data.virtualnode.VirtualNodeUpdateRequest
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.orm.utils.transaction
import net.corda.virtualnode.toCorda
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeConnectionStrings
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbFactory
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.VirtualNodeAsyncOperationHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception.VirtualNodeUpgradeRejectedException
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.factories.RecordFactory
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.services.UpdateVirtualNodeService
import org.slf4j.Logger
import java.time.Instant
import javax.persistence.EntityManagerFactory

@Suppress("LongParameterList")
internal class UpdateVirtualNodeOperationHandler(
    private val entityManagerFactory: EntityManagerFactory,
    private val createVirtualNodeService: UpdateVirtualNodeService,
    private val virtualNodeDbFactory: VirtualNodeDbFactory,
    private val recordFactory: RecordFactory,
    statusPublisher: Publisher,
    private val logger: Logger,
    private val virtualNodeRepository: VirtualNodeRepository = VirtualNodeRepositoryImpl(),
): VirtualNodeAsyncOperationHandler<VirtualNodeUpdateRequest>,
    AbstractVirtualNodeOperationHandler(statusPublisher, logger) {
    override fun handle(requestTimestamp: Instant, requestId: String, request: VirtualNodeUpdateRequest) {
        publishStartProcessingStatus(requestId)

        try {
            val holdingId = request.holdingId.toCorda()
            val x500Name = holdingId.x500Name.toString()

            logger.info("Update Virtual Node: $x500Name")
            val execLog = ExecutionTimeLogger("Create", x500Name, requestTimestamp.toEpochMilli(), logger)

            val requestValidationResult = execLog.measureExecTime("validation") {
                createVirtualNodeService.validateRequest(request)
            }
            require(requestValidationResult == null) { "$requestValidationResult" }

            val vNodeDbs = execLog.measureExecTime("get virtual node databases") {
                virtualNodeDbFactory.createVNodeDbs(holdingId.shortHash, with (request) {
                    VirtualNodeConnectionStrings(
                        vaultDdlConnection,
                        vaultDmlConnection,
                        cryptoDdlConnection,
                        cryptoDmlConnection,
                        uniquenessDdlConnection,
                        uniquenessDmlConnection
                    )
                })
            }

            val currentVirtualNode =
                entityManagerFactory.createEntityManager().transaction { em ->
                    virtualNodeRepository.find(em, holdingId.shortHash)
                        ?: throw VirtualNodeUpgradeRejectedException("Holding identity ${holdingId.shortHash} not found", requestId)
                }


            val vNodeConnections = execLog.measureExecTime("persist holding ID and virtual node") {
                createVirtualNodeService.persistHoldingIdAndVirtualNode(
                    holdingId,
                    vNodeDbs,
                    currentVirtualNode.cpiIdentifier,
                    request.updateActor,
                    currentVirtualNode.externalMessagingRouteConfig
                )
            }

            execLog.measureExecTime("publish virtual node and MGM info") {
                createVirtualNodeService.publishRecords(listOf(recordFactory.createVirtualNodeInfoRecord(
                    holdingId,
                    currentVirtualNode.cpiIdentifier,
                    vNodeConnections,
                    currentVirtualNode.externalMessagingRouteConfig
                )))
            }
        } catch (e: Exception) {
            publishErrorStatus(requestId, e.message ?: "Unexpected error")
            throw e
        }

        publishProcessingCompletedStatus(requestId)
    }
}
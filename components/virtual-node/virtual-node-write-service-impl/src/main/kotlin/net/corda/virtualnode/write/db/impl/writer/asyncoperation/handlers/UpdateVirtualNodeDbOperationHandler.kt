package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import net.corda.data.virtualnode.VirtualNodeDbConnectionUpdateRequest
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
internal class UpdateVirtualNodeDbOperationHandler(
    private val entityManagerFactory: EntityManagerFactory,
    private val updateVirtualNodeService: UpdateVirtualNodeService,
    private val virtualNodeDbFactory: VirtualNodeDbFactory,
    private val recordFactory: RecordFactory,
    statusPublisher: Publisher,
    private val logger: Logger,
    private val virtualNodeRepository: VirtualNodeRepository = VirtualNodeRepositoryImpl(),
): VirtualNodeAsyncOperationHandler<VirtualNodeDbConnectionUpdateRequest>,
    AbstractVirtualNodeOperationHandler(statusPublisher, logger) {
    override fun handle(requestTimestamp: Instant, requestId: String, request: VirtualNodeDbConnectionUpdateRequest) {
        publishStartProcessingStatus(requestId)

        try {
            val holdingId = request.holdingId.toCorda()
            val x500Name = holdingId.x500Name.toString()

            logger.info("Update Virtual Node DB connection: $x500Name")
            val execLog = ExecutionTimeLogger("vNode DB Update", x500Name, requestTimestamp.toEpochMilli(), logger)

            val requestValidationResult = execLog.measureExecTime("validation") {
                updateVirtualNodeService.validateRequest(request)
            }
            require(requestValidationResult == null) { "$requestValidationResult" }

            val vNodeDbs = execLog.measureExecTime("create virtual node databases") {
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
                updateVirtualNodeService.persistHoldingIdAndVirtualNode(
                    holdingId,
                    vNodeDbs,
                    currentVirtualNode.cpiIdentifier,
                    request.updateActor,
                    currentVirtualNode.externalMessagingRouteConfig
                )
            }

            execLog.measureExecTime("publish virtual node and MGM info") {
                updateVirtualNodeService.publishRecords(listOf(recordFactory.createVirtualNodeInfoRecord(
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
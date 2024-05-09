package net.corda.virtualnode.rest.converters.impl

import net.corda.libs.external.messaging.entities.InactiveResponseType
import net.corda.libs.external.messaging.entities.Route
import net.corda.libs.external.messaging.entities.RouteConfiguration
import net.corda.libs.external.messaging.entities.Routes
import net.corda.libs.external.messaging.serialization.ExternalMessagingRouteConfigSerializer
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeInfo
import net.corda.rest.asynchronous.v1.AsyncOperationStatus
import net.corda.virtualnode.rest.converters.MessageConverter
import net.corda.data.virtualnode.VirtualNodeOperationStatus as AvroVirtualNodeOperationStatus
import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier as CpiIdentifierRestResponse
import net.corda.libs.packaging.core.CpiIdentifier as CpiIdentifierDto
import net.corda.libs.virtualnode.endpoints.v1.types.HoldingIdentity as HoldingIdentityRestResponse
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeInfo as VirtualNodeInfoRestResponse
import net.corda.libs.virtualnode.endpoints.v1.types.external.messaging.RouteConfiguration as RouteConfigurationRestResponse
import net.corda.libs.virtualnode.endpoints.v1.types.external.messaging.Routes as RoutesRestResponse
import net.corda.libs.virtualnode.endpoints.v1.types.external.messaging.Route as RouteRestResponse
import net.corda.libs.virtualnode.endpoints.v1.types.external.messaging.InactiveResponseType as InactiveResponseTypeRestResponse
import net.corda.virtualnode.HoldingIdentity as HoldingIdentityDto
import net.corda.virtualnode.VirtualNodeInfo as VirtualNodeInfoDto

class MessageConverterImpl(
    private val routeConfigSerializer: ExternalMessagingRouteConfigSerializer
) : MessageConverter {

    // Avro Message states
    companion object {
        const val ACCEPTED = "ACCEPTED"
        const val IN_PROGRESS = "IN_PROGRESS"
        const val VALIDATION_FAILED = "VALIDATION_FAILED"
        const val LIQUIBASE_DIFF_CHECK_FAILED = "LIQUIBASE_DIFF_CHECK_FAILED"
        const val MIGRATIONS_FAILED = "MIGRATIONS_FAILED"
        const val UNEXPECTED_FAILURE = "UNEXPECTED_FAILURE"
        const val COMPLETED = "COMPLETED"
        const val ABORTED = "ABORTED"
    }

    override fun convert(virtualNodeInfoDto: VirtualNodeInfoDto): VirtualNodeInfoRestResponse {
        val routeConfig = virtualNodeInfoDto.externalMessagingRouteConfig?.let {
            routeConfigSerializer.deserialize(it)
        }

        return with(virtualNodeInfoDto) {
            VirtualNodeInfo(
                holdingIdentity.toEndpointType(),
                cpiIdentifier.toEndpointType(),
                vaultDdlConnectionId?.toString(),
                vaultDmlConnectionId.toString(),
                cryptoDdlConnectionId?.toString(),
                cryptoDmlConnectionId.toString(),
                uniquenessDdlConnectionId?.toString(),
                uniquenessDmlConnectionId.toString(),
                hsmConnectionId.toString(),
                flowP2pOperationalStatus,
                flowStartOperationalStatus,
                flowOperationalStatus,
                vaultDbOperationalStatus,
                operationInProgress,
                routeConfig?.toEndpointType()
            )
        }
    }

    @Suppress("LongMethod", "UseCheckOrError")
    override fun convert(
        status: AvroVirtualNodeOperationStatus,
        operation: String,
        resourceId: String?
    ): AsyncOperationStatus {
        return when (status.state) {
            ACCEPTED -> {
                AsyncOperationStatus.accepted(status.requestId, operation, status.latestUpdateTimestamp)
            }

            IN_PROGRESS -> {
                AsyncOperationStatus.inProgress(status.requestId, operation, status.latestUpdateTimestamp)
            }

            VALIDATION_FAILED -> {
                AsyncOperationStatus.failed(
                    status.requestId,
                    operation,
                    status.latestUpdateTimestamp,
                    status.errors,
                    "Validation"
                )
            }

            LIQUIBASE_DIFF_CHECK_FAILED -> {
                AsyncOperationStatus.failed(
                    status.requestId,
                    operation,
                    status.latestUpdateTimestamp,
                    status.errors,
                    "Liquibase diff check"
                )
            }

            MIGRATIONS_FAILED -> {
                AsyncOperationStatus.failed(
                    status.requestId,
                    operation,
                    status.latestUpdateTimestamp,
                    status.errors,
                    "Migration"
                )
            }

            UNEXPECTED_FAILURE -> {
                AsyncOperationStatus.failed(
                    status.requestId,
                    operation,
                    status.latestUpdateTimestamp,
                    status.errors
                )
            }

            ABORTED -> {
                AsyncOperationStatus.aborted(
                    status.requestId,
                    operation,
                    status.latestUpdateTimestamp
                )
            }

            COMPLETED -> {
                AsyncOperationStatus.succeeded(
                    status.requestId,
                    operation,
                    status.latestUpdateTimestamp,
                    resourceId
                )
            }

            else -> {
                throw IllegalStateException("The virtual node operation status '${status.state}' is not recognized.")
            }
        }
    }

    private fun HoldingIdentityDto.toEndpointType(): HoldingIdentityRestResponse =
        HoldingIdentityRestResponse(
            x500Name.toString(),
            groupId,
            shortHash.value,
            fullHash
        )

    private fun CpiIdentifierDto.toEndpointType(): CpiIdentifierRestResponse =
        CpiIdentifierRestResponse(name, version, signerSummaryHash.toString())

    private fun RouteConfiguration.toEndpointType(): RouteConfigurationRestResponse =
        RouteConfigurationRestResponse(
            currentRoutes.toEndpointType(),
            previousVersionRoutes.map { it.toEndpointType() }
        )

    private fun Routes.toEndpointType(): RoutesRestResponse =
        RoutesRestResponse(
            cpiIdentifier.toEndpointType(),
            routes.map { it.toEndpointType() }
        )


    private fun Route.toEndpointType(): RouteRestResponse =
        RouteRestResponse(
            channelName,
            externalReceiveTopicName,
            active,
            inactiveResponseType.toEndpointType()
        )

    private fun InactiveResponseType.toEndpointType(): InactiveResponseTypeRestResponse =
        when (this) {
            InactiveResponseType.ERROR -> InactiveResponseTypeRestResponse.ERROR
            InactiveResponseType.IGNORE -> InactiveResponseTypeRestResponse.IGNORE
        }
}

package net.corda.virtualnode.write.db.impl.writer

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.common.PermissionTypeDto
import net.corda.libs.permissions.manager.request.CreatePermissionRequestDto
import net.corda.libs.permissions.manager.request.CreateRoleRequestDto
import net.corda.virtualnode.HoldingIdentity
import org.slf4j.LoggerFactory

internal object VirtualNodeRbacCreator {

    private val logger = LoggerFactory.getLogger(VirtualNodeRbacCreator::class.java)

    fun PermissionManager.createRbacRole(holdingId: HoldingIdentity, flowNames: Set<String>, actor: String) {

        val vnodeShortHash = holdingId.shortHash.toString()
        val flowNamesPermissionsCreated = flowNames.map { flowName ->
            val permCreateRequest = CreatePermissionRequestDto(
                actor,
                PermissionTypeDto.ALLOW,
                "FlowStart:$flowName",
                null,
                vnodeShortHash
            )
            createPermission(permCreateRequest).id
        }

        val flowOperationsPermissionsCreated = setOf(
            "POST:/api/v1/flow/$vnodeShortHash",
            "GET:/api/v1/flow/$vnodeShortHash.*",
            "WS:/api/v1/flow/$vnodeShortHash/.*"
        ).map {
            val permCreateRequest = CreatePermissionRequestDto(
                actor,
                PermissionTypeDto.ALLOW,
                it,
                null,
                vnodeShortHash
            )
            createPermission(permCreateRequest).id
        }

        val roleName = "FlowExecutorRole-$vnodeShortHash"
        val createRoleRequestDto = CreateRoleRequestDto(actor, roleName, null)
        val roleId = createRole(createRoleRequestDto).id

        (flowNamesPermissionsCreated + flowOperationsPermissionsCreated).forEach { permId ->
            addPermissionToRole(roleId, permId, actor)
        }

        logger.info("Created role named: $roleName with id: $roleId.")
    }

    fun CpiInfoReadService.getFlowNames(cpiMetadataLite: CpiMetadataLite): Set<String> {
        val cpiMetaData = requireNotNull(get(cpiMetadataLite.id))
        return cpiMetaData.cpksMetadata.flatMap { cpkMeta -> cpkMeta.cordappManifest.rpcStartableFlows }.toSet()
    }
}
package net.corda.libs.permissions.endpoints.v1.user.impl

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.libs.permissions.endpoints.common.PermissionEndpointEventHandler
import net.corda.libs.permissions.endpoints.v1.converter.convertToDto
import net.corda.libs.permissions.endpoints.v1.converter.convertToEndpointType
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.libs.permissions.endpoints.common.withPermissionManager
import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.endpoints.v1.user.types.UserPermissionSummaryResponseType
import net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
import net.corda.libs.permissions.manager.request.AddRoleToUserRequestDto
import net.corda.libs.permissions.manager.request.GetPermissionSummaryRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.request.RemoveRoleFromUserRequestDto
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.management.PermissionManagementService
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * An RPC Ops endpoint for User operations.
 */
@Component(service = [PluggableRPCOps::class])
class UserEndpointImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionManagementService::class)
    private val permissionManagementService: PermissionManagementService,
) : UserEndpoint, PluggableRPCOps<UserEndpoint>, Lifecycle {

    private companion object {
        val logger = contextLogger()
    }

    override val targetInterface: Class<UserEndpoint> = UserEndpoint::class.java

    override val protocolVersion = 1

    private val coordinator = coordinatorFactory.createCoordinator<UserEndpoint>(
        PermissionEndpointEventHandler("UserEndpoint")
    )

    override fun createUser(createUserType: CreateUserType): UserResponseType {
        val principal = getRpcThreadLocalContext()

        val createUserResult = withPermissionManager(permissionManagementService.permissionManager, logger) {
            createUser(createUserType.convertToDto(principal))
        }

        return createUserResult!!.convertToEndpointType()
    }

    override fun getUser(loginName: String): UserResponseType {
        val principal = getRpcThreadLocalContext()

        val userResponseDto = withPermissionManager(permissionManagementService.permissionManager, logger) {
            getUser(GetUserRequestDto(principal, loginName.lowercase()))
        }

        return userResponseDto?.convertToEndpointType() ?: throw ResourceNotFoundException("User", loginName)
    }

    override fun addRole(loginName: String, roleId: String): UserResponseType {
        val principal = getRpcThreadLocalContext()

        val result = withPermissionManager(permissionManagementService.permissionManager, logger) {
            addRoleToUser(AddRoleToUserRequestDto(principal, loginName.lowercase(), roleId))
        }
        return result!!.convertToEndpointType()
    }

    override fun removeRole(loginName: String, roleId: String): UserResponseType {
        val principal = getRpcThreadLocalContext()

        val result = withPermissionManager(permissionManagementService.permissionManager, logger) {
            removeRoleFromUser(RemoveRoleFromUserRequestDto(principal, loginName.lowercase(), roleId))
        }
        return result!!.convertToEndpointType()
    }

    override fun getPermissionSummary(loginName: String): UserPermissionSummaryResponseType {
        val principal = getRpcThreadLocalContext()

        val result = withPermissionManager(permissionManagementService.permissionManager, logger) {
            getPermissionSummary(GetPermissionSummaryRequestDto(principal, loginName.lowercase()))
        } ?: throw ResourceNotFoundException("User", loginName)

        return UserPermissionSummaryResponseType(
            result.loginName,
            result.enabled,
            result.permissions.map { it.convertToEndpointType() },
            result.lastUpdateTimestamp
        )
    }

    private fun getRpcThreadLocalContext(): String {
        val rpcContext = CURRENT_RPC_CONTEXT.get()
        return rpcContext.principal
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.close()
    }
}

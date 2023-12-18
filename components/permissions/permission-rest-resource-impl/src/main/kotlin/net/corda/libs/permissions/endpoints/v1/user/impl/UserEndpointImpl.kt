package net.corda.libs.permissions.endpoints.v1.user.impl

import net.corda.libs.permissions.common.constant.RoleKeys.DEFAULT_SYSTEM_ADMIN_ROLE
import net.corda.libs.permissions.common.constant.UserKeys.DEFAULT_ADMIN_FULL_NAME
import net.corda.libs.permissions.endpoints.common.PermissionEndpointEventHandler
import net.corda.libs.permissions.endpoints.common.withPermissionManager
import net.corda.libs.permissions.endpoints.v1.RoleUtils.initialAdminRole
import net.corda.libs.permissions.endpoints.v1.converter.convertToDto
import net.corda.libs.permissions.endpoints.v1.converter.convertToEndpointType
import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.endpoints.v1.user.types.UserPermissionSummaryResponseType
import net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.request.AddRoleToUserRequestDto
import net.corda.libs.permissions.manager.request.ChangeUserPasswordDto
import net.corda.libs.permissions.manager.request.GetPermissionSummaryRequestDto
import net.corda.libs.permissions.manager.request.GetRoleRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.request.RemoveRoleFromUserRequestDto
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.management.PermissionManagementService
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.InvalidStateChangeException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.response.ResponseEntity
import net.corda.rest.security.CURRENT_REST_CONTEXT
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A REST resource endpoint for User operations.
 */
@Component(service = [PluggableRestResource::class])
class UserEndpointImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionManagementService::class)
    private val permissionManagementService: PermissionManagementService,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
) : UserEndpoint, PluggableRestResource<UserEndpoint>, Lifecycle {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        @Suppress("ThrowsCount")
        private fun PermissionManager.checkProtectedRole(loginName: String, roleId: String, principal: String) {
            val role = getRole(GetRoleRequestDto(principal, roleId))
                ?: throw ResourceNotFoundException("Role ID", roleId)

            val user = getUser(GetUserRequestDto(principal, loginName))
                ?: throw ResourceNotFoundException("User login", loginName)

            if (role.initialAdminRole && user.fullName == DEFAULT_ADMIN_FULL_NAME) {
                throw BadRequestException(
                    "$DEFAULT_SYSTEM_ADMIN_ROLE cannot be removed from $DEFAULT_ADMIN_FULL_NAME",
                    mapOf("roleId" to roleId, "loginName" to loginName)
                )
            }
        }
    }

    override val targetInterface: Class<UserEndpoint> = UserEndpoint::class.java

    override val protocolVersion get() = platformInfoProvider.localWorkerPlatformVersion

    private val coordinator = coordinatorFactory.createCoordinator<UserEndpoint>(
        PermissionEndpointEventHandler("UserEndpoint")
    )

    override fun createUser(createUserType: CreateUserType): ResponseEntity<UserResponseType> {
        val principal = getRestThreadLocalContext()

        val createUserResult = withPermissionManager(permissionManagementService.permissionManager, logger) {
            createUser(createUserType.convertToDto(principal))
        }

        return ResponseEntity.created(createUserResult.convertToEndpointType())
    }

    @Deprecated("Deprecated in favour of `getUserPath()`")
    override fun getUserQuery(loginName: String): ResponseEntity<UserResponseType> {
        "Deprecated, please use next version where loginName is passed as a path parameter.".let { msg ->
            logger.warn(msg)
            return ResponseEntity.okButDeprecated(doGetUser(loginName), msg)
        }
    }

    override fun getUserPath(loginName: String): UserResponseType {
        return doGetUser(loginName)
    }

    private fun doGetUser(loginName: String): UserResponseType {
        val principal = getRestThreadLocalContext()

        val userResponseDto = withPermissionManager(permissionManagementService.permissionManager, logger) {
            getUser(GetUserRequestDto(principal, loginName.lowercase()))
        }

        return userResponseDto?.convertToEndpointType() ?: throw ResourceNotFoundException("User", loginName)
    }

    override fun changeUserPasswordSelf(loginName: String, password: String): UserResponseType {
        val principal = getRestThreadLocalContext()

        val userResponseDto = try {
            withPermissionManager(permissionManagementService.permissionManager, logger) {
                changeUserPasswordSelf(ChangeUserPasswordDto(principal, loginName, password))
            }
        } catch (e: IllegalArgumentException) {
            throw InvalidStateChangeException(e.message ?: "New password must be different from old one.")
        }

        return userResponseDto.convertToEndpointType()
    }

    override fun changeOtherUserPassword(loginName: String, password: String): UserResponseType {
        val principal = getRestThreadLocalContext()

        val userResponseDto = try {
            withPermissionManager(permissionManagementService.permissionManager, logger) {
                changeUserPasswordOther(ChangeUserPasswordDto(principal, loginName, password))
            }
        } catch (e: IllegalArgumentException) {
            throw InvalidStateChangeException(e.message ?: "New password must be different from old one.")
        }

        return userResponseDto.convertToEndpointType()
    }

    override fun addRole(loginName: String, roleId: String): ResponseEntity<UserResponseType> {
        val principal = getRestThreadLocalContext()

        val result = withPermissionManager(permissionManagementService.permissionManager, logger) {
            addRoleToUser(AddRoleToUserRequestDto(principal, loginName.lowercase(), roleId))
        }
        return ResponseEntity.ok(result.convertToEndpointType())
    }

    override fun removeRole(loginName: String, roleId: String): ResponseEntity<UserResponseType> {
        val principal = getRestThreadLocalContext()

        val result = withPermissionManager(permissionManagementService.permissionManager, logger) {
            checkProtectedRole(loginName, roleId, principal)
            removeRoleFromUser(RemoveRoleFromUserRequestDto(principal, loginName.lowercase(), roleId))
        }
        return ResponseEntity.deleted(result.convertToEndpointType())
    }

    override fun getPermissionSummary(loginName: String): UserPermissionSummaryResponseType {
        val principal = getRestThreadLocalContext()

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

    private fun getRestThreadLocalContext(): String {
        val restContext = CURRENT_REST_CONTEXT.get()
        return restContext.principal
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}

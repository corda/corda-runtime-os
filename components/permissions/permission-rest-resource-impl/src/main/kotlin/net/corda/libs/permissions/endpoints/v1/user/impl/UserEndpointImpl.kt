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
import net.corda.libs.permissions.endpoints.v1.user.types.PropertyResponseType
import net.corda.libs.permissions.endpoints.v1.user.types.UserPermissionSummaryResponseType
import net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.request.AddPropertyToUserRequestDto
import net.corda.libs.permissions.manager.request.AddRoleToUserRequestDto
import net.corda.libs.permissions.manager.request.ChangeUserPasswordDto
import net.corda.libs.permissions.manager.request.DeleteUserRequestDto
import net.corda.libs.permissions.manager.request.GetPermissionSummaryRequestDto
import net.corda.libs.permissions.manager.request.GetRoleRequestDto
import net.corda.libs.permissions.manager.request.GetUserPropertiesRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.request.GetUsersByPropertyRequestDto
import net.corda.libs.permissions.manager.request.RemovePropertyFromUserRequestDto
import net.corda.libs.permissions.manager.request.RemoveRoleFromUserRequestDto
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.management.PermissionManagementService
import net.corda.rest.PluggableRestResource
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.authorization.AuthorizationProvider
import net.corda.rest.authorization.AuthorizingSubject
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.ExceptionDetails
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.response.ResponseEntity
import net.corda.rest.security.CURRENT_REST_CONTEXT
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions

/**
 * A REST resource endpoint for User operations.
 */
@Suppress("TooManyFunctions")
@Component(service = [PluggableRestResource::class])
class UserEndpointImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionManagementService::class)
    private val permissionManagementService: PermissionManagementService,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
) : UserEndpoint, PluggableRestResource<UserEndpoint>, Lifecycle {

    private val changeSelfPasswordMethodPath: String

    init {
        changeSelfPasswordMethodPath = this::class.memberFunctions
            .firstOrNull { it.name == ::changeUserPasswordSelf.name }
            ?.let { method ->
                UserEndpoint::class.memberFunctions.find { it.name == method.name }?.findAnnotation<HttpPOST>()
            }
            ?.path ?: throw IllegalStateException("changeUserPasswordSelf method path not found")
    }

    override val authorizationProvider = object : AuthorizationProvider {
        override fun isAuthorized(subject: AuthorizingSubject, action: String): Boolean {
            val requestedPath = action.split(":", limit = 2).last()

            // if requested Path is for /selfpassword we override the default authorization, as all users
            // should be able to change their password
            return if (requestedPath.endsWith(changeSelfPasswordMethodPath)) {
                true
            } else {
                AuthorizationProvider.Default.isAuthorized(subject, action)
            }
        }
    }

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val INVALID_ARGUMENT_MESSAGE = "Invalid argument in request."

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

        val createUserResult = try {
            withPermissionManager(permissionManagementService.permissionManager, logger) {
                createUser(createUserType.convertToDto(principal))
            }
        } catch (e: IllegalArgumentException) {
            throw InvalidInputDataException(
                title = e::class.java.simpleName,
                exceptionDetails = ExceptionDetails(e::class.java.name, e.message ?: INVALID_ARGUMENT_MESSAGE)
            )
        }

        return ResponseEntity.created(createUserResult.convertToEndpointType())
    }

    override fun deleteUser(loginName: String): ResponseEntity<UserResponseType> {
        val principal = getRestThreadLocalContext()

        if (principal.equals(loginName, ignoreCase = true)) {
            throw BadRequestException("User cannot delete self")
        }

        val userResponseDto = withPermissionManager(permissionManagementService.permissionManager, logger) {
            deleteUser(DeleteUserRequestDto(principal, loginName.lowercase()))
        }

        return ResponseEntity.deleted(userResponseDto.convertToEndpointType())
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

    override fun changeUserPasswordSelf(password: String): UserResponseType {
        val principal = getRestThreadLocalContext()

        val userResponseDto = withPermissionManager(permissionManagementService.permissionManager, logger) {
            try {
                changeUserPasswordSelf(ChangeUserPasswordDto(principal, principal.lowercase(), password))
            } catch (e: NoSuchElementException) {
                throw ResourceNotFoundException(
                    e::class.java.simpleName,
                    ExceptionDetails(e::class.java.name, e.message ?: "No resource found for this request.")
                )
            } catch (e: IllegalArgumentException) {
                throw InvalidInputDataException(
                    title = e::class.java.simpleName,
                    exceptionDetails = ExceptionDetails(e::class.java.name, e.message ?: INVALID_ARGUMENT_MESSAGE)
                )
            }
        }

        return userResponseDto.convertToEndpointType()
    }

    override fun changeOtherUserPassword(username: String, password: String): UserResponseType {
        val principal = getRestThreadLocalContext()

        val userResponseDto = withPermissionManager(permissionManagementService.permissionManager, logger) {
            try {
                changeUserPasswordOther(ChangeUserPasswordDto(principal, username.lowercase(), password))
            } catch (e: NoSuchElementException) {
                throw ResourceNotFoundException(
                    e::class.java.simpleName,
                    ExceptionDetails(e::class.java.name, e.message ?: "No resource found for this request.")
                )
            } catch (e: IllegalArgumentException) {
                throw InvalidInputDataException(
                    title = e::class.java.simpleName,
                    exceptionDetails = ExceptionDetails(e::class.java.name, e.message ?: INVALID_ARGUMENT_MESSAGE)
                )
            }
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

    override fun addProperty(loginName: String, properties: Map<String, String>): ResponseEntity<UserResponseType> {
        val principal = getRestThreadLocalContext()

        val result = withPermissionManager(permissionManagementService.permissionManager, logger) {
            addPropertyToUser(AddPropertyToUserRequestDto(principal, loginName.lowercase(), properties))
        }
        return ResponseEntity.ok(result.convertToEndpointType())
    }

    override fun removeProperty(loginName: String, propertyKey: String): ResponseEntity<UserResponseType> {
        val principal = getRestThreadLocalContext()
        val result = withPermissionManager(permissionManagementService.permissionManager, logger) {
            removePropertyFromUser(RemovePropertyFromUserRequestDto(principal, loginName.lowercase(), propertyKey))
        }
        return ResponseEntity.deleted(result.convertToEndpointType())
    }

    override fun getUserProperties(loginName: String): ResponseEntity<List<PropertyResponseType>> {
        val principal = getRestThreadLocalContext()
        val result = withPermissionManager(permissionManagementService.permissionManager, logger) {
            getUserProperties(GetUserPropertiesRequestDto(principal, loginName.lowercase()))
        } ?: throw ResourceNotFoundException("User", loginName)
        return ResponseEntity.ok(result.map { it.convertToEndpointType() })
    }

    override fun getUsersByPropertyKey(propertyKey: String, propertyValue: String): ResponseEntity<List<UserResponseType>> {
        val principal = getRestThreadLocalContext()
        val result = withPermissionManager(permissionManagementService.permissionManager, logger) {
            getUsersByProperty(GetUsersByPropertyRequestDto(principal, propertyKey, propertyValue))
        } ?: throw ResourceNotFoundException("Value", propertyValue)
        return ResponseEntity.ok(result.map { it.convertToEndpointType() })
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

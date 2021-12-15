package net.corda.libs.permissions.manager.impl

import java.time.Duration
import net.corda.data.permissions.User
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.manager.PermissionUserManager
import net.corda.libs.permissions.manager.exception.PermissionManagerException
import net.corda.libs.permissions.manager.impl.converter.convertToResponseDto
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.messaging.api.publisher.RPCSender
import net.corda.permissions.password.PasswordService
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.Try

class PermissionUserManagerImpl(
    config: SmartConfig,
    private val rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
    private val permissionCache: PermissionCache,
    private val passwordService: PasswordService
) : PermissionUserManager {

    private companion object {
        const val REMOTE_WRITER_TIMEOUT_PATH = "permission.management.remoteWriterTimeoutMs"
        const val DEFAULT_WRITER_TIMEOUT_MS = 10000L
    }

    private val writerTimeout = initializeEndpointTimeoutDuration(config)

    private fun initializeEndpointTimeoutDuration(config: SmartConfig): Duration {
        return if (config.hasPath(REMOTE_WRITER_TIMEOUT_PATH)) {
            Duration.ofMillis(config.getLong(REMOTE_WRITER_TIMEOUT_PATH))
        } else {
            Duration.ofMillis(DEFAULT_WRITER_TIMEOUT_MS)
        }
    }

    override fun createUser(createUserRequestDto: CreateUserRequestDto): Try<UserResponseDto> {
        return Try.on {
            val saltAndHash = createUserRequestDto.initialPassword?.let {
                passwordService.saltAndHash(it)
            }

            val future = rpcSender.sendRequest(
                PermissionManagementRequest(
                    createUserRequestDto.requestedBy,
                    "cluster",
                    CreateUserRequest(
                        createUserRequestDto.fullName,
                        createUserRequestDto.loginName,
                        createUserRequestDto.enabled,
                        saltAndHash?.value,
                        saltAndHash?.salt,
                        createUserRequestDto.passwordExpiry,
                        createUserRequestDto.parentGroup
                    )
                )
            )

            val futureResponse = future.getOrThrow(writerTimeout)

            val result = futureResponse.response
            if (result !is User)
                throw PermissionManagerException("Unknown response for Create User operation: $result")

            result.convertToResponseDto()
        }
    }

    override fun getUser(userRequestDto: GetUserRequestDto): UserResponseDto? {
        val cachedUser: User = permissionCache.getUser(userRequestDto.loginName) ?: return null
        return cachedUser.convertToResponseDto()
    }
}
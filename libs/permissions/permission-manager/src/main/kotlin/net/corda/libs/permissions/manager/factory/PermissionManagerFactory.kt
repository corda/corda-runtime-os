package net.corda.libs.permissions.manager.factory

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.messaging.api.publisher.RPCSender
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.cache.PermissionManagementCache
import net.corda.libs.permissions.cache.PermissionValidationCache
import net.corda.libs.permissions.manager.BasicAuthenticationService

/**
 * The [PermissionManagerFactory] constructs instances of [PermissionManager].
 */
interface PermissionManagerFactory {
    /**
     * Create a [PermissionManager].
     *
     * @param config the configuration for the permission manager.
     * @param rpcSender the [RPCSender] responsible for posting requests of type [PermissionManagementRequest] and accepting responses of
     * type [PermissionManagementResponse].
     * @param permissionManagementCache the permission management cache holding permission data used for permission management read
     * operations.
     * @param permissionValidationCache the cache holding data used for permission validation
     */
    fun createPermissionManager(
        config: SmartConfig,
        rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
        permissionManagementCache: PermissionManagementCache,
        permissionValidationCache: PermissionValidationCache
    ): PermissionManager

    /**
     * Create a service for performing basic authentication utilizing the permission management cache.
     */
    fun createBasicAuthenticationService(
        permissionManagementCache: PermissionManagementCache
    ): BasicAuthenticationService
}
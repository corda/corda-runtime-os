package net.corda.libs.permissions.manager.factory

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.messaging.api.publisher.RPCSender
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.libs.permissions.manager.BasicAuthenticationService
import java.util.concurrent.atomic.AtomicReference

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
     * @param permissionManagementCacheRef the permission management cache holding permission data used for permission management read
     * operations.
     * @param permissionValidationCache the cache holding data used for permission validation
     */
    fun createPermissionManager(
        config: SmartConfig,
        rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
        permissionManagementCacheRef: AtomicReference<PermissionManagementCache?>,
        permissionValidationCache: PermissionValidationCache
    ): PermissionManager

    /**
     * Create a service for performing basic authentication utilizing the permission management cache.
     */
    fun createBasicAuthenticationService(
        permissionManagementCacheRef: AtomicReference<PermissionManagementCache?>
    ): BasicAuthenticationService
}
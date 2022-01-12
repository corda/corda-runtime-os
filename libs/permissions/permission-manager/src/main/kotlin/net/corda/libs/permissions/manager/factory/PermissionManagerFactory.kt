package net.corda.libs.permissions.manager.factory

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.messaging.api.publisher.RPCSender
import net.corda.libs.configuration.SmartConfig

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
     * @param permissionCache the [PermissionCache] holding permission data which may be useful for some permission management operations.
     */
    fun create(
        config: SmartConfig,
        rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
        permissionCache: PermissionCache
    ): PermissionManager
}
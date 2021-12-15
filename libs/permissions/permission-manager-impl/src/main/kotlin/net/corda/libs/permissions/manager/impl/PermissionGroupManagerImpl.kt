package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.manager.PermissionGroupManager
import net.corda.messaging.api.publisher.RPCSender

class PermissionGroupManagerImpl(
    private val config: SmartConfig,
    private val rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
    private val permissionCache: PermissionCache
) : PermissionGroupManager
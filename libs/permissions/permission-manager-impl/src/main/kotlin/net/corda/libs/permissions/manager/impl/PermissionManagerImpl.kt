package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.permissions.manager.PermissionEntityManager
import net.corda.libs.permissions.manager.PermissionGroupManager
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.PermissionRoleManager
import net.corda.libs.permissions.manager.PermissionUserManager
import net.corda.messaging.api.publisher.RPCSender

class PermissionManagerImpl(
    private val rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
    private val permissionUserManager: PermissionUserManager,
    private val permissionGroupManager: PermissionGroupManager,
    private val permissionRoleManager: PermissionRoleManager,
    private val permissionEntityManager: PermissionEntityManager
) : PermissionManager,
    PermissionUserManager by permissionUserManager,
    PermissionGroupManager by permissionGroupManager,
    PermissionRoleManager by permissionRoleManager,
    PermissionEntityManager by permissionEntityManager {

    @Volatile
    private var started = false

    override val isRunning: Boolean
        get() = started && rpcSender.isRunning

    override fun start() {
        started = true
    }

    override fun stop() {
        started = false
    }
}
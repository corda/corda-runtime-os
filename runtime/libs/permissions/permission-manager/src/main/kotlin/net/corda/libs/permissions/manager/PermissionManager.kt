package net.corda.libs.permissions.manager

import net.corda.libs.permissions.manager.factory.PermissionManagerFactory
import net.corda.lifecycle.Lifecycle

/**
 * The [PermissionManager] provides functionality for permission management and has a lifecycle.
 *
 * Construct instances of this interface using [PermissionManagerFactory].
 */
interface PermissionManager : PermissionUserManager, PermissionGroupManager, PermissionRoleManager,
    PermissionEntityManager, Lifecycle
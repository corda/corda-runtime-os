package net.corda.libs.permission.factory

import com.typesafe.config.Config
import net.corda.libs.permission.PermissionService

interface PermissionServiceFactory {

    /**
     * Create an instance of the [PermissionService]
     */
    fun createPermissionService(): PermissionService
}
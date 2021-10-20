package net.corda.libs.permission.factory

import com.typesafe.config.Config
import net.corda.libs.permission.PermissionService

interface PermissionServiceFactory {

    /**
     * Create an instance of the [PermissionService]
     * @param bootstrapConfig configuration object used to bootstrap the read service
     */
    fun createPermissionService(bootstrapConfig: Config): PermissionService
}
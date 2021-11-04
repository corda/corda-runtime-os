package net.corda.rpc.permissions

import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permission.factory.PermissionServiceFactory
import net.corda.lifecycle.Lifecycle
import org.osgi.service.component.annotations.Reference

class PermissionServiceComponent(
    @Reference(service = PermissionServiceFactory::class)
    private val permissionServiceFactory: PermissionServiceFactory
) : Lifecycle {

    @Volatile
    private var permissionValidator: PermissionValidator? = null

    override val isRunning: Boolean
        get() = permissionValidator.let { it?.isRunning ?: false }

    override fun start() {
        permissionValidator?.stop()
        permissionValidator = permissionServiceFactory.createPermissionService()
    }

    override fun stop() {
        permissionValidator?.stop()
    }
}
package net.corda.rpc.permissions

import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permission.factory.PermissionValidatorFactory
import net.corda.lifecycle.Lifecycle
import org.osgi.service.component.annotations.Reference

class PermissionServiceComponent(
    @Reference(service = PermissionValidatorFactory::class)
    private val permissionValidatorFactory: PermissionValidatorFactory
) : Lifecycle {

    @Volatile
    private var permissionValidator: PermissionValidator? = null

    override val isRunning: Boolean
        get() = permissionValidator.let { it?.isRunning ?: false }

    override fun start() {
        if (permissionValidator == null) {
            permissionValidator = permissionValidatorFactory.createPermissionValidator().also { it.start() }
        }
    }

    override fun stop() {
        permissionValidator?.stop()
        permissionValidator = null
    }
}
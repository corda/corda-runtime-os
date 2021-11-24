package net.corda.rpc.permissions

import net.corda.libs.permissions.validation.PermissionValidator
import net.corda.libs.permissions.validation.factory.PermissionValidatorFactory
import net.corda.lifecycle.Lifecycle
import org.osgi.service.component.annotations.Reference

class PermissionValidationService(
    @Reference(service = PermissionValidatorFactory::class)
    private val permissionValidatorFactory: PermissionValidatorFactory
) : Lifecycle {

    val permissionValidator: PermissionValidator
        get() {
            checkNotNull(_permissionValidator) {
                "Permission Validator is null. Getter should be called only after service is UP."
            }
            return _permissionValidator!!
        }

    @Volatile
    private var _permissionValidator: PermissionValidator? = null

    override val isRunning: Boolean
        get() = _permissionValidator.let { it?.isRunning ?: false }

    override fun start() {
        if (_permissionValidator == null) {
            _permissionValidator = permissionValidatorFactory.createPermissionValidator().also { it.start() }
        }
    }

    override fun stop() {
        _permissionValidator?.stop()
        _permissionValidator = null
    }
}
package net.corda.libs.permissions.manager.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

class PermissionManagerException(message: String) : CordaRuntimeException(message)
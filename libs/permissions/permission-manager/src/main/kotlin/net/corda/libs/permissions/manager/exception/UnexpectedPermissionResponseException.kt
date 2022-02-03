package net.corda.libs.permissions.manager.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

class UnexpectedPermissionResponseException(message: String) : CordaRuntimeException(message)
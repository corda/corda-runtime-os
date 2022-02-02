package net.corda.libs.permissions.manager.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

class RemotePermissionManagementException(
    val exceptionType: String,
    message: String
) : CordaRuntimeException(message)
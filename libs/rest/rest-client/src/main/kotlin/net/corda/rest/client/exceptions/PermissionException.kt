package net.corda.rest.client.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

class PermissionException(message: String) : CordaRuntimeException(message)
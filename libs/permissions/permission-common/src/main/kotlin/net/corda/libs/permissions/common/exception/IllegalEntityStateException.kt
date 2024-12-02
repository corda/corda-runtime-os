package net.corda.libs.permissions.common.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

class IllegalEntityStateException(message: String) : CordaRuntimeException(message)

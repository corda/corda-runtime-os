package net.corda.dependency.injection

import net.corda.v5.base.exceptions.CordaRuntimeException

class CordaInjectableException(message: String?) : CordaRuntimeException(message)
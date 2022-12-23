package net.corda.cpi.persistence

import net.corda.v5.base.exceptions.CordaRuntimeException

class CpiPersistenceValidationException(msg: String) : CordaRuntimeException(msg)

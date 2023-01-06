package net.corda.crypto.core

import net.corda.v5.base.exceptions.CordaRuntimeException

class KeyAlreadyExistsException(message: String, val alias: String, val tenantId: String) :
    CordaRuntimeException(message)

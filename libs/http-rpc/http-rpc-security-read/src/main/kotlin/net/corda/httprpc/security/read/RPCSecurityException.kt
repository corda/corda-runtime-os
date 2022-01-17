package net.corda.httprpc.security.read

import net.corda.v5.base.exceptions.CordaRuntimeException

class RPCSecurityException(message: String) : CordaRuntimeException(message)
package net.corda.httprpc.client.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

class InternalErrorException(message: String) : CordaRuntimeException(message)
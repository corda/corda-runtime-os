package net.corda.httprpc.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

open class HttpApiException(message: String, val statusCode: Int) : CordaRuntimeException(message)
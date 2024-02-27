package net.corda.rest.client.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Cannot use [javax.net.ssl.SSLHandshakeException] since it is a checked exception
 */
class ClientSslHandshakeException(message: String) : CordaRuntimeException(message)

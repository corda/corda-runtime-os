package net.corda.crypto.component.impl

import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.v5.crypto.exceptions.CryptoException
import net.corda.v5.crypto.exceptions.CryptoRetryException
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import javax.management.openmbean.KeyAlreadyExistsException

val exceptionFactories = mapOf<String, (String, Throwable) -> Throwable>(
    IllegalArgumentException::class.java.name to { m, e -> IllegalArgumentException(m, e) },
    IllegalStateException::class.java.name to { m, e -> IllegalStateException(m, e) },
    CryptoSignatureException::class.java.name to { m, e -> CryptoSignatureException(m, e) },
    CryptoRetryException::class.java.name to { m, e -> CryptoRetryException(m, e) },
    KeyAlreadyExistsException::class.java.name to { m, _ -> KeyAlreadyExistsException(m) },
)

fun CordaRPCAPIResponderException.toClientException() =
    exceptionFactories[errorType]?.invoke(message.safeMessage(), this)
        ?: CryptoException(message.safeMessage(), this)

private fun String?.safeMessage() = this ?: "Failed to execute crypto operation."

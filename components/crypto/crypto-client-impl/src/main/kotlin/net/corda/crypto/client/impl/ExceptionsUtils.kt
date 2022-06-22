package net.corda.crypto.client.impl

import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.v5.crypto.failures.CryptoException
import net.corda.v5.crypto.failures.CryptoRetryException
import net.corda.v5.crypto.failures.CryptoSignatureException

internal val exceptionFactories = mapOf<String, (String, Throwable) -> Throwable>(
    IllegalArgumentException::class.java.name to { m, e -> IllegalArgumentException(m, e) },
    IllegalStateException::class.java.name to { m, e -> IllegalStateException(m, e) },
    CryptoSignatureException::class.java.name to { m, e -> CryptoSignatureException(m, e) },
    CryptoRetryException::class.java.name to { m, e -> CryptoRetryException(m, e) },
)

fun CordaRPCAPIResponderException.toClientException() =
    exceptionFactories[errorType]?.invoke(message.safeMessage(), this)
        ?: CryptoException(message.safeMessage(), this)

private fun String?.safeMessage() = this ?: "Failed to execute crypto operation."

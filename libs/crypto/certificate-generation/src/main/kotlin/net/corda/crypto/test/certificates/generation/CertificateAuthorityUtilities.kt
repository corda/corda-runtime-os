package net.corda.crypto.test.certificates.generation

import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.StringWriter
import java.security.Key
import java.security.cert.Certificate

/**
 * Convert a Certificate to PEM string.
 */
fun Certificate.toPem(): String {
    return StringWriter().use { str ->
        JcaPEMWriter(str).use { writer ->
            writer.writeObject(this)
        }
        str.toString()
    }
}
/**
 * Convert a Certificate collection to PEM string.
 */
fun Collection<Certificate>.toPem(): String {
    return this.map {
        it.toPem()
    }.joinToString(separator = "\n")
}

/**
 * Convert a Key to PEM string.
 */
fun Key.toPem(): String {
    return StringWriter().use { str ->
        JcaPEMWriter(str).use { writer ->
            writer.writeObject(this)
        }
        str.toString()
    }
}

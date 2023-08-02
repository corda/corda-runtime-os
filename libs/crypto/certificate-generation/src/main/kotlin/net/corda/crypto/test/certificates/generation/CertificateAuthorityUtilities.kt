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

package net.corda.crypto.test.certificates.generation

import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.StringWriter
import java.security.Key
import java.security.KeyStore
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

/**
 * Convert a certificate to a key store object (with alias "alias")
 */
fun Certificate.toKeystore(): KeyStore {
    return KeyStore.getInstance("PKCS12").also { keyStore ->
        keyStore.load(null)
        keyStore.setCertificateEntry("alias", this)
    }
}

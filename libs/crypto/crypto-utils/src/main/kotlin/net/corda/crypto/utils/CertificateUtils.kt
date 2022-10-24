package net.corda.crypto.utils

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import org.slf4j.LoggerFactory

private const val KEY_STORE_TYPE = "PKCS12"
typealias PemCertificate = String

fun convertToKeyStore(certificateFactory: CertificateFactory, pemCertificates: Collection<PemCertificate>, alias: String): KeyStore? {
    val logger = LoggerFactory.getLogger("net.corda.crypto.utils.CertificateUtils")
    return KeyStore.getInstance(KEY_STORE_TYPE).also { keyStore ->
        keyStore.load(null, null)
        pemCertificates.withIndex().forEach { (index, pemCertificate) ->
            val certificate = ByteArrayInputStream(pemCertificate.toByteArray()).use {
                try {
                    certificateFactory.generateCertificate(it)
                } catch (except: CertificateException) {
                    logger.warn("Could not load certificate: ${except.message}.")
                    return null
                }
            }
            try {
                keyStore.setCertificateEntry("$alias-$index", certificate)
            } catch (except: KeyStoreException) {
                logger.warn("Could not load certificate into keystore: ${except.message}.")
                return null
            }
        }
    }
}
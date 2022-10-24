package net.corda.p2p.linkmanager

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import org.slf4j.LoggerFactory

private const val KEY_STORE_TYPE = "PKCS12"

internal fun convertToKeyStore(certificateFactory: CertificateFactory, pemCertificates: Collection<String>): KeyStore? {
    val logger = LoggerFactory.getLogger(" net.corda.p2p.linkmanager.CertificateUtils")
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
                keyStore.setCertificateEntry("session-$index", certificate)
            } catch (except: KeyStoreException) {
                logger.warn("Could not load certificate into keystore: ${except.message}.")
                return null
            }
        }
    }
}
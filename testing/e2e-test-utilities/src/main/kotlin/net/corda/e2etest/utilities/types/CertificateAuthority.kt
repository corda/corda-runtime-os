package net.corda.e2etest.utilities.types

import net.corda.crypto.test.certificates.generation.Algorithm.Companion.toAlgorithm
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.FileSystemCertificatesAuthority
import net.corda.crypto.test.certificates.generation.KeysFactoryDefinitions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.e2etest.utilities.CAT_SESSION_INIT
import net.corda.e2etest.utilities.CAT_TLS
import net.corda.e2etest.utilities.CERT_ALIAS_SESSION
import net.corda.e2etest.utilities.CERT_USAGE_P2P
import net.corda.e2etest.utilities.CERT_USAGE_SESSION
import net.corda.e2etest.utilities.ClusterInfo
import net.corda.e2etest.utilities.DEFAULT_KEY_SCHEME
import net.corda.e2etest.utilities.TENANT_P2P
import net.corda.e2etest.utilities.createKeyFor
import net.corda.e2etest.utilities.disableCertificateRevocationChecks
import net.corda.e2etest.utilities.generateCsr
import net.corda.e2etest.utilities.importCertificate
import net.corda.e2etest.utilities.whenNoKeyExists
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.File
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CertificateAuthority private constructor(
    val ca: FileSystemCertificatesAuthority,
    val name: String,
) {
    companion object {
        private val caLock = ReentrantLock()
        private const val DEFAULT_NAME = "default"
        private const val CERT_ALIAS_P2P_PREFIX = "p2p-tls-cert"

        /**
         * Get the default CA for testing. This is written to file so it can be shared across tests.
         */
        val default: CertificateAuthority
            get() = caLock.withLock {
                create(DEFAULT_NAME)
            }

        /**
         * Create a new CA.
         */
        fun new() =
            create(UUID.randomUUID().toString())

        private fun create(
            name: String,
        ) =
            CertificateAuthorityFactory
                .createFileSystemLocalAuthority(
                    KeysFactoryDefinitions("RSA".toAlgorithm(), 3072, null),
                    File("build${File.separator}tmp${File.separator}$name${File.separator}ca"),
                ).also { it.save() }
                .let {
                    CertificateAuthority(
                        it,
                        name,
                    )
                }
    }

    /**
     * Generate a certificate from a CSR as a PEM string.
     * The certificate is also returned as a PEM string.
     */
    fun generateCert(csrPem: String): String {
        val request = csrPem.reader().use { reader ->
            PEMParser(reader).use { parser ->
                parser.readObject()
            }
        }?.also {
            assertThat(it).isInstanceOf(PKCS10CertificationRequest::class.java)
        }
        return caLock.withLock {
            ca.signCsr(request as PKCS10CertificationRequest)
                .also {
                    ca.save()
                }.toPem()
        }
    }

    val caCertificatePem by lazy {
        ca.caCertificate.toPem()
    }

    fun importTlsCertificateIfNotExists(
        cluster: ClusterInfo,
        afterImport: (String) -> Unit = {},
    ): String {
        val alias = "$TENANT_P2P$CAT_TLS$name"
        val certificateAlias = "$CERT_ALIAS_P2P_PREFIX-$name"
        cluster.whenNoKeyExists(TENANT_P2P, alias = alias, category = CAT_TLS) {
            cluster.disableCertificateRevocationChecks()
            val tlsKeyId = cluster.createKeyFor(
                TENANT_P2P,
                alias,
                CAT_TLS,
                DEFAULT_KEY_SCHEME,
            )
            val tlsCsr = cluster.generateCsr(
                "CN=R3, OU=Test, O=TlsCertificate, L=London, S=London, C=GB",
                tlsKeyId,
            )
            val tlsCertificateFile = File.createTempFile(
                "${this.hashCode()}$CAT_TLS",
                ".pem",
            ).also {
                it.deleteOnExit()
                it.writeText(
                    generateCert(tlsCsr),
                )
            }
            cluster.importCertificate(tlsCertificateFile, CERT_USAGE_P2P, certificateAlias)
            afterImport(tlsCsr)
        }
        return certificateAlias
    }

    fun importSessionCertificate(
        cluster: ClusterInfo,
        name: String,
        sessionKeyId: String,
        holdingId: String,
    ): String {
        val alias = "$CERT_ALIAS_SESSION-$holdingId-$name"
        val sessionCsr = cluster.generateCsr(name, sessionKeyId, holdingId)
        val certificate = generateCert(sessionCsr)
        val sessionCertFile = File.createTempFile(
            "${this.hashCode()}$CAT_SESSION_INIT",
            ".pem",
        ).also {
            it.deleteOnExit()
            it.writeText(certificate)
        }
        cluster.importCertificate(sessionCertFile, CERT_USAGE_SESSION, alias, holdingId)
        return alias
    }
}

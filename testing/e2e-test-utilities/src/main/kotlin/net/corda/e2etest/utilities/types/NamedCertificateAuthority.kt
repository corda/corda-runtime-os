package net.corda.e2etest.utilities.types

import net.corda.crypto.test.certificates.generation.FileSystemCertificatesAuthority
import net.corda.e2etest.utilities.CAT_TLS
import net.corda.e2etest.utilities.CERT_USAGE_P2P
import net.corda.e2etest.utilities.ClusterInfo
import net.corda.e2etest.utilities.DEFAULT_KEY_SCHEME
import net.corda.e2etest.utilities.TENANT_P2P
import net.corda.e2etest.utilities.createCa
import net.corda.e2etest.utilities.createKeyFor
import net.corda.e2etest.utilities.disableCertificateRevocationChecks
import net.corda.e2etest.utilities.generateCert
import net.corda.e2etest.utilities.generateCsr
import net.corda.e2etest.utilities.importCertificate
import net.corda.e2etest.utilities.keyExists
import java.io.File
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val importLocker = ReentrantLock()
fun ClusterInfo.importTlsCertificate(
    ca: NamedCertificateAuthority,
    x500Name: String,
    tlsCertificateUploadedCallback: (String) -> Unit = {},
): String {
    val keyAlias = "TLS-${ca.name}"
    val certificateAlias = "RLS-CERTIFICATE-${ca.name}"
    importLocker.withLock {
        if (!keyExists(TENANT_P2P, keyAlias, CAT_TLS)) {
            disableCertificateRevocationChecks()
            val tlsKeyId = createKeyFor(TENANT_P2P, keyAlias, CAT_TLS, DEFAULT_KEY_SCHEME)
            val tlsCsr = generateCsr(x500Name, tlsKeyId)
            val tlsCert = ca.ca.generateCert(tlsCsr)
            val tlsCertFile = File.createTempFile("${this.hashCode()}$CAT_TLS", ".pem").also {
                it.deleteOnExit()
                it.writeText(tlsCert)
            }
            importCertificate(tlsCertFile, CERT_USAGE_P2P, certificateAlias)
            tlsCertificateUploadedCallback(tlsCert)
        }
    }
    return certificateAlias
}

data class NamedCertificateAuthority(
    val name: String = UUID.randomUUID().toString(),
    val ca: FileSystemCertificatesAuthority = createCa(name),
) {
    companion object {
        fun default(): NamedCertificateAuthority = NamedCertificateAuthority("default")
    }
}
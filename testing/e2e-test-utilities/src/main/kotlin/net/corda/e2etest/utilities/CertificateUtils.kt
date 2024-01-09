package net.corda.e2etest.utilities

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.test.certificates.generation.Algorithm.Companion.toAlgorithm
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.FileSystemCertificatesAuthority
import net.corda.crypto.test.certificates.generation.KeysFactoryDefinitions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.e2etest.utilities.config.SingleClusterTestConfigManager
import net.corda.rest.ResponseCode
import net.corda.rest.annotations.RestApiVersion
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.seconds
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Get the default CA for testing. This is written to file so it can be shared across tests.
 */
private val caLock = ReentrantLock()
fun getCa(): FileSystemCertificatesAuthority = caLock.withLock {
    CertificateAuthorityFactory
        .createFileSystemLocalAuthority(
            KeysFactoryDefinitions("RSA".toAlgorithm(), 3072, null,),
            File("build${File.separator}tmp${File.separator}ca")
        ).also { it.save() }
}

/**
 * Generate a certificate from a CSR as a PEM string.
 * The certificate is also returned as a PEM string.
 */
fun FileSystemCertificatesAuthority.generateCert(csrPem: String): String {
    val request = csrPem.reader().use { reader ->
        PEMParser(reader).use { parser ->
            parser.readObject()
        }
    }?.also {
        assertThat(it).isInstanceOf(PKCS10CertificationRequest::class.java)
    }
    return caLock.withLock {
        signCsr(request as PKCS10CertificationRequest).also { save() }.toPem()
    }
}

/**
 * Attempt to generate a CSR for a key. This calls the REST API for a given cluster and returns the generate CSR as a
 * PEM string.
 */
fun ClusterInfo.generateCsr(
    x500Name: String,
    keyId: String,
    tenantId: String = "p2p",
    addHostToSubjectAlternativeNames: Boolean = true
) = cluster {
    val payload = mutableMapOf<String, Any>(
        "x500Name" to x500Name
    ).apply {
        if (addHostToSubjectAlternativeNames) {
            put("subjectAlternativeNames", listOf(this@generateCsr.p2p.host))
        }
    }

    assertWithRetryIgnoringExceptions {
        interval(1.seconds)
        if (restApiVersion == RestApiVersion.C5_0) {
            command {
                post(
                    "/api/${RestApiVersion.C5_0.versionPath}/certificates/$tenantId/$keyId",
                    ObjectMapper().writeValueAsString(payload)
                )
            }
        } else {
            command {
                post(
                    "/api/${restApiVersion.versionPath}/certificate/$tenantId/$keyId",
                    ObjectMapper().writeValueAsString(payload)
                )
            }
        }
        condition { it.code == ResponseCode.OK.statusCode }
    }.body
}

/**
 * Imports a certificate to a given Corda cluster from file.
 */
fun ClusterInfo.importCertificate(
    file: File,
    usage: String,
    alias: String,
    holdingIdentity: String? = null
) {
    cluster {
        assertWithRetryIgnoringExceptions {
            interval(1.seconds)
            command { importCertificate(file, usage, alias, holdingIdentity) }
            condition { it.code == ResponseCode.NO_CONTENT.statusCode }
        }
    }
}


/**
 * Disable certificate revocation checks.
 * CRL checks disabled is the default for E2E tests so this doesn't attempt to revert after use.
 */
fun ClusterInfo.disableCertificateRevocationChecks() {
    SingleClusterTestConfigManager(this)
        .load(ConfigKeys.P2P_GATEWAY_CONFIG, "sslConfig.revocationCheck.mode", "OFF")
        .load(ConfigKeys.P2P_GATEWAY_CONFIG, "sslConfig.tlsType", TlsType.type.configName)
        .applyWithoutRevert {}
}
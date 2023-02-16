package net.corda.e2etest.utilities

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.test.certificates.generation.Algorithm.Companion.toAlgorithm
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.FileSystemCertificatesAuthority
import net.corda.crypto.test.certificates.generation.KeysFactoryDefinitions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.httprpc.ResponseCode
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.File

/**
 * Get the default CA for testing. This is written to file so it can be shared across tests.
 */
fun getCa(): FileSystemCertificatesAuthority = CertificateAuthorityFactory
    .createFileSystemLocalAuthority(
        KeysFactoryDefinitions("RSA".toAlgorithm(), 3072, null,),
        File("build${File.separator}tmp${File.separator}ca")
    ).also { it.save() }

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
    return signCsr(request as PKCS10CertificationRequest).also { save() }.toPem()
}

/**
 * Attempt to generate a CSR for a key. This calls the REST API for a given cluster and returns the generate CSR as a
 * PEM string.
 */
fun generateCsr(
    clusterConfig: ClusterConfig,
    x500Name: MemberX500Name,
    keyId: String,
    tenantId: String = "p2p",
    addHostToSubjectAlternativeNames: Boolean = true
) = cluster(clusterConfig) {
    val payload = mutableMapOf<String, Any>(
        "x500Name" to x500Name.toString()
    ).apply {
        if (addHostToSubjectAlternativeNames) {
            put("subjectAlternativeNames", listOf(clusterConfig.p2pHost))
        }
    }

    assertWithRetry {
        command { post("/api/v1/certificates/$tenantId/$keyId", ObjectMapper().writeValueAsString(payload)) }
        condition { it.code == ResponseCode.OK.statusCode }
    }.body
}

/**
 * Imports a certificate to a given Corda cluster from file.
 */
fun importCertificate(
    clusterConfig: ClusterConfig,
    file: File,
    usage: String,
    alias: String
) {
    cluster(clusterConfig) {
        assertWithRetry {
            command { importCertificate(file, usage, alias) }
            condition { it.code == ResponseCode.NO_CONTENT.statusCode }
        }
    }
}
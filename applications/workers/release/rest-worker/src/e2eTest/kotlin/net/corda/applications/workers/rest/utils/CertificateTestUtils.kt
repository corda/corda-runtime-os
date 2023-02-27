package net.corda.applications.workers.rest.utils

import net.corda.crypto.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.FileSystemCertificatesAuthority
import net.corda.crypto.test.certificates.generation.toFactoryDefinitions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.httprpc.HttpFileUpload
import net.corda.membership.rest.v1.CertificatesRestResource
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.File


private val caPath = "build${File.separator}tmp${File.separator}e2eTestCa"
const val TLS_CERT_ALIAS = "p2p-tls-cert"
const val SESSION_CERT_ALIAS = "p2p-session"

fun getCa(): FileSystemCertificatesAuthority = CertificateAuthorityFactory
    .createFileSystemLocalAuthority(
        RSA_TEMPLATE.toFactoryDefinitions(),
        File(caPath)
    ).also { it.save() }


fun FileSystemCertificatesAuthority.generateCert(csrPem: String): String {
    val request = csrPem.reader().use { reader ->
        PEMParser(reader).use { parser ->
            parser.readObject()
        }
    }?.also {
        assertThat(it).isInstanceOf(PKCS10CertificationRequest::class.java)
    }
    return signCsr(request as PKCS10CertificationRequest).also{ save() }.toPem()
}

fun E2eCluster.generateCsr(
    member: E2eClusterMember,
    keyId: String,
    tenantId: String = P2P_TENANT_ID,
    addHostToSubjectAlternativeNames: Boolean = true
): String {
    val subjectAlternativeNames = if (addHostToSubjectAlternativeNames) {
        listOf(clusterConfig.p2pHost)
    } else {
        null
    }
    return clusterHttpClientFor(CertificatesRestResource::class.java)
        .use { client ->
            client.start().proxy.generateCsr(
                tenantId = tenantId,
                keyId = keyId,
                x500Name = member.name,
                subjectAlternativeNames = subjectAlternativeNames,
                contextMap = null
            )
        }
}

fun E2eCluster.uploadTlsCertificate(
    certificatePem: String
) {
    clusterHttpClientFor(CertificatesRestResource::class.java).use { client ->
        client.start().proxy.importCertificateChain(
            usage = "p2p-tls",
            alias = TLS_CERT_ALIAS,
            certificates = listOf(
                HttpFileUpload(
                    certificatePem.byteInputStream(),
                    "$TLS_CERT_ALIAS.pem"
                )
            )
        )
    }
}

fun E2eCluster.uploadSessionCertificate(
    certificatePem: String,
    holdingIdentityId: String
) {
    clusterHttpClientFor(CertificatesRestResource::class.java).use { client ->
        client.start().proxy.importCertificateChain(
            usage = "p2p-session",
            alias = SESSION_CERT_ALIAS,
            holdingIdentityId = holdingIdentityId,
            certificates = listOf(
                HttpFileUpload(
                    certificatePem.byteInputStream(),
                    "$SESSION_CERT_ALIAS-$holdingIdentityId.pem"
                )
            )
        )
    }
}
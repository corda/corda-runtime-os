package net.corda.applications.workers.rpc.utils

import net.corda.crypto.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.FileSystemCertificatesAuthority
import net.corda.crypto.test.certificates.generation.toFactoryDefinitions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.httprpc.HttpFileUpload
import net.corda.membership.httprpc.v1.CertificatesRpcOps
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.File


private val caPath = "build${File.separator}tmp${File.separator}e2eTestCa"
const val TLS_CERT_ALIAS = "p2p-tls-cert"

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
    return signCsr(request as PKCS10CertificationRequest).toPem()
}

fun E2eCluster.generateCsr(
    member: E2eClusterMember,
    tlsKeyId: String
): String {
    return clusterHttpClientFor(CertificatesRpcOps::class.java)
        .use { client ->
            client.start().proxy.generateCsr(
                P2P_TENANT_ID,
                tlsKeyId,
                member.name,
                listOf(clusterConfig.p2pHost),
                null
            )
        }
}

fun E2eCluster.uploadTlsCertificate(
    certificatePem: String
) {
    clusterHttpClientFor(CertificatesRpcOps::class.java).use { client ->
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
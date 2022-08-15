package net.corda.applications.workers.rpc.utils

import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.FileSystemCertificatesAuthority
import net.corda.crypto.test.certificates.generation.toFactoryDefinitions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.httprpc.HttpFileUpload
import net.corda.membership.httprpc.v1.CertificatesRpcOps
import net.corda.v5.cipher.suite.schemes.RSA_TEMPLATE
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.File


private val tmpPath = "build${File.separator}tmp"
private val caPath = "$tmpPath${File.separator}e2eTestCa"
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

fun ClusterTestData.generateCsr(
    member: MemberTestData,
    tlsKeyId: String
) = with(testToolkit) {
    httpClientFor(CertificatesRpcOps::class.java)
        .use { client ->
            client.start().proxy.generateCsr(
                P2P_TENANT_ID,
                tlsKeyId,
                member.name,
                HSM_CAT_TLS,
                listOf(p2pHost),
                null
            )
        }
}

fun ClusterTestData.uploadTlsCertificate(
    certificatePem: String
) = with(testToolkit) {
    httpClientFor(CertificatesRpcOps::class.java).use { client ->
        client.start().proxy.importCertificateChain(
            P2P_TENANT_ID,
            TLS_CERT_ALIAS,
            listOf(
                HttpFileUpload(
                    certificatePem.byteInputStream(),
                    "$TLS_CERT_ALIAS.pem"
                )
            )
        )
    }
}
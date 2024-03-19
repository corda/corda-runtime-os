package net.corda.crypto.test.certificates.generation

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HandlerType
import net.corda.crypto.test.certificates.generation.LocalCertificatesAuthority.Companion.signer
import org.bouncycastle.asn1.x509.CRLReason
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder
import org.bouncycastle.cert.ocsp.CertificateStatus
import org.bouncycastle.cert.ocsp.OCSPReq
import org.bouncycastle.cert.ocsp.OCSPResp
import org.bouncycastle.cert.ocsp.OCSPRespBuilder
import org.bouncycastle.cert.ocsp.RespID
import org.bouncycastle.cert.ocsp.RevokedStatus
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.encoders.Base64
import java.math.BigInteger
import java.net.URLDecoder
import java.security.InvalidParameterException
import java.security.cert.Certificate
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.time.Clock
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

internal class RevocableCertificateAuthorityImpl(
    private val authority: LocalCertificatesAuthority,
    port: Int,
) : RevocableCertificateAuthority, CertificateAuthority by authority {
    companion object {
        internal const val PATH = "/ca.crl"
    }
    private val app = Javalin.create().start(port).addHandler(HandlerType.GET, PATH, CrlHandler())
        .addHandler(HandlerType.GET, "$PATH/*", OcspHandler())
    private val revokedCertificates = ConcurrentHashMap.newKeySet<BigInteger>()
    private val clock = Clock.systemUTC()

    override fun revoke(certificate: Certificate) {
        if (certificate is X509Certificate) {
            revokedCertificates.add(certificate.serialNumber)
        } else {
            throw InvalidParameterException()
        }
    }

    override fun reintroduce(certificate: Certificate) {
        if (certificate is X509Certificate) {
            revokedCertificates.remove(certificate.serialNumber)
        } else {
            throw InvalidParameterException()
        }
    }

    override fun close() {
        app.close()
    }

    private inner class CrlHandler : Handler {
        override fun handle(context: Context) {
            val crl = createCrl()
            context.result(crl.encoded)
        }

        private fun createCrl(): X509CRL {
            val now = clock.instant().toEpochMilli()
            val crlGen = JcaX509v2CRLBuilder(
                (authority.caCertificate as X509Certificate).subjectX500Principal,
                Date(now),
            )
            crlGen.setNextUpdate(Date(now + 100))
            revokedCertificates.forEach { serialNumber ->
                val extGen = ExtensionsGenerator()
                val reason = CRLReason.lookup(CRLReason.privilegeWithdrawn)
                extGen.addExtension(Extension.reasonCode, false, reason)
                crlGen.addCRLEntry(
                    serialNumber,
                    Date(now - 1000),
                    extGen.generate(),
                )
            }
            val signer = authority.privateKeyAndCertificate.privateKey.signer()
            val converter = JcaX509CRLConverter().setProvider(BouncyCastleProvider())
            return converter.getCRL(crlGen.build(signer))
        }
    }
    private inner class OcspHandler : Handler {
        override fun handle(context: Context) {
            val request = OCSPReq(
                Base64.decode(
                    URLDecoder.decode(
                        context.path()
                            .removePrefix("$PATH/"),
                        "UTF-8",
                    ),
                ),
            )
            val responseBuilder = BasicOCSPRespBuilder(
                RespID(
                    authority.issuer,
                ),
            )
            val now = clock.instant().toEpochMilli()
            request.requestList.forEach { req ->
                val certId = req.certID
                val status = if (!revokedCertificates.contains(certId.serialNumber)) {
                    CertificateStatus.GOOD
                } else {
                    RevokedStatus(Date(now - 1000))
                }
                responseBuilder.addResponse(certId, status, Date(now), Date(now + 1000))
            }

            val signer = authority.privateKeyAndCertificate.privateKey.signer()

            val response = OCSPRespBuilder().build(
                OCSPResp.SUCCESSFUL,
                responseBuilder.build(signer, request.certs, Date(now)),
            )

            context.result(response.encoded)
        }
    }
}

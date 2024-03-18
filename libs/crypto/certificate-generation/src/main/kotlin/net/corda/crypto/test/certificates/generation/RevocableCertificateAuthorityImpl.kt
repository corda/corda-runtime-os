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
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
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
): RevocableCertificateAuthority, CertificateAuthority by authority, Handler {
    companion object {
        internal const val PATH = "/ca.crl"
    }
    private val app = Javalin.create().start(port).addHandler(HandlerType.GET, PATH, this)
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

    override fun handle(context: Context) {
        val crl = createCrl()
        context.result(crl.encoded)
    }

    private fun createCrl() : X509CRL  {
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
            crlGen.addCRLEntry(serialNumber,
                Date(now), extGen.generate())
        }
        val signer = authority.privateKeyAndCertificate.privateKey.signer()
        val converter = JcaX509CRLConverter().setProvider(BouncyCastleProvider())
        return converter.getCRL(crlGen.build(signer))
    }

}
package net.corda.libs.packaging.verify

import java.security.CodeSigner
import java.security.cert.CertPathValidator
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

/** Verifies that signatures lead to trusted certificate */
internal fun verifyCertificates(codeSigners: List<CodeSigner>, trustedCerts: Collection<X509Certificate>) {
    require(codeSigners.isNotEmpty()) {
        "Code signers not set"
    }
    require(trustedCerts.isNotEmpty()) {
        "Trusted certificates not set"
    }

    val params = trustedCerts
        .mapTo(HashSet()) { TrustAnchor(it, null) }
        .let(::PKIXParameters)
    params.isRevocationEnabled = false

    val certPathValidator = CertPathValidator.getInstance("PKIX")
    codeSigners.forEach {
        certPathValidator.validate(it.signerCertPath, params)
    }
}
package net.corda.libs.packaging.verify

import java.security.CodeSigner
import java.security.Timestamp
import java.security.cert.CertPath
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate


/** Verifies that code signers' signatures are valid */
internal fun verifyCertificates(codeSigners: List<CodeSigner>, trustedCerts: Collection<X509Certificate>) {
    require(codeSigners.isNotEmpty()) {
        "Code signers not set"
    }
    require(trustedCerts.isNotEmpty()) {
        "Trusted certificates not set"
    }

    codeSigners.forEach {
        validateCertPath(it.signerCertPath, "code signer's", trustedCerts, it.timestamp)
        if (it.timestamp != null) {
            validateCertPath(it.timestamp.signerCertPath, "TSA", trustedCerts)
        }
    }
}

/** Validates [certPath] against [trustedCerts] checking also signature [timestamp] if provided */
internal fun validateCertPath(
    certPath: CertPath,
    certPathName :String,
    trustedCerts: Collection<X509Certificate>,
    timestamp: Timestamp? = null
) {
    require(certPath.certificates.isNotEmpty()) {
        "Certificates not set"
    }
    require(trustedCerts.isNotEmpty()) {
        "Trusted certificates not set"
    }

    val params = trustedCerts
        .mapTo(HashSet()) { TrustAnchor(it, null) }
        .let(::PKIXParameters)
    params.isRevocationEnabled = false
    if (timestamp != null) {
        params.date = timestamp.timestamp
    }

    val certPathValidator = CertPathValidator.getInstance("PKIX")
    try {
        certPathValidator.validate(certPath, params)
    } catch (e: CertPathValidatorException) {
        val index = if (e.index >= 0) ", certificate at index [${e.index}]" else ""
        val msg = "Error validating $certPathName certificate path [$certPath]$index: ${e.message}"
        throw CertPathValidatorException(msg, e.cause, e.certPath, e.index, e.reason)
    }
}
package net.corda.libs.packaging.verify

import java.security.CodeSigner
import java.security.Timestamp
import java.security.cert.*


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
        val index: String; val cert: String?
        if (e.index >= 0) {
            index = ", certificate at index [${e.index}]"
            cert = certPath.certificates[e.index].toString()
        } else {
            index = ""
            cert = if (certPath.certificates.size == 1) certPath.certificates.first().toString() else null
        }

        var name = ""
        if (cert != null) {
            val nameStart = cert.indexOf("Subject:")
            val nameEnd = cert.indexOf("\n", nameStart)
            name = if (nameStart != -1 && nameEnd != -1) cert.substring(nameStart+9, nameEnd) else ""
        }

        val msg = "Error validating $certPathName certificate path$index, ${certPath.type} name: $name. ${e.message}"
        throw CertPathValidatorException(msg, e.cause, e.certPath, e.index, e.reason)
    }
}
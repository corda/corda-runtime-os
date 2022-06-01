package net.corda.libs.packaging.verify

import net.corda.libs.packaging.verify.internal.VerifierFactory
import java.io.InputStream
import java.security.cert.X509Certificate

/**
 * Performs following CPK verification checks:
 * * Package was not tampered
 * * Signatures are valid and lead to [trustedCerts]
 * * Manifest has required attributes set
 * * CPK library constraints
 * */
fun verifyCpk(name: String, inputStream: InputStream, trustedCerts: Collection<X509Certificate>) {
    VerifierFactory.createCpkVerifier(name, inputStream, trustedCerts).verify()
}

/**
 * Performs following CPB verification checks:
 * * Package was not tampered
 * * Signatures are valid and lead to [trustedCerts]
 * * Manifest has required attributes set
 * * All included CPKs are valid
 * * CPKs' dependencies are satisfied
 * */
fun verifyCpb(name: String, inputStream: InputStream, trustedCerts: Collection<X509Certificate>) {
    VerifierFactory.createCpbVerifier(name, inputStream, trustedCerts).verify()
}

/**
 * Performs following CPI verification checks:
 * * Package was not tampered
 * * Signatures are valid and lead to [trustedCerts]
 * * Manifest has required attributes set
 * * Included CPB is valid
 * * Policy file is valid
 * */
fun verifyCpi(name: String, inputStream: InputStream, trustedCerts: Collection<X509Certificate>) {
    VerifierFactory.createCpiVerifier(name, inputStream, trustedCerts).verify()
}
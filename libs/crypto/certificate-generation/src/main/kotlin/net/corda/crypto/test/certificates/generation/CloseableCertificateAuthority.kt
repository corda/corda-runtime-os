package net.corda.crypto.test.certificates.generation

/**
 * A certificate authority that should be closed after usage.
 */
interface CloseableCertificateAuthority : CertificateAuthority, AutoCloseable

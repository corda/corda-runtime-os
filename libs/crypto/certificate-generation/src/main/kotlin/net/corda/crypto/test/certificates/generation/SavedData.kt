package net.corda.crypto.test.certificates.generation

internal data class SavedData(
    val privateKeyAndCertificate: PrivateKeyWithCertificateChain,
    val firstSerialNumber: Long,
)
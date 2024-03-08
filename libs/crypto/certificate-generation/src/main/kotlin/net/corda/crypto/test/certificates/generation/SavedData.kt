package net.corda.crypto.test.certificates.generation

internal data class SavedData(
    val privateKeyAndCertificate: PrivateKeyWithCertificate,
    val firstSerialNumber: Long,
)
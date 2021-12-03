package net.corda.p2p.linkmanager.utilities

import net.corda.v5.base.util.toHex
import org.bouncycastle.asn1.x500.AttributeTypeAndValue
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest

object SourceCalculator {

    private const val HASH_ALGO = "SHA-256"
    private const val HASH_TRUNCATION_SIZE = 32

    fun calculateSource(
        x500Name: String
    ): String {
        val x500NameSorted =
            X500Name(x500Name).rdNs.flatMap { it.typesAndValues.asList() }.sortedBy { it.type.toString() }
                .groupBy(AttributeTypeAndValue::getType, AttributeTypeAndValue::getValue)
                .mapValues { it.value[0].toString() }.map { it }.joinToString(", ")
        sha256Hash(x500NameSorted.toByteArray()).toHex().take(HASH_TRUNCATION_SIZE).toLowerCase()
        return x500NameSorted
    }

    private fun sha256Hash(bytes: ByteArray): ByteArray {
        val provider = BouncyCastleProvider()
        val messageDigest = MessageDigest.getInstance(HASH_ALGO, provider)
        messageDigest.reset()
        messageDigest.update(bytes)
        return messageDigest.digest()
    }
}
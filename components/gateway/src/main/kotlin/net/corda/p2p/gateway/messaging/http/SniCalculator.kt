package net.corda.p2p.gateway.messaging.http

import java.security.MessageDigest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.net.URI
import net.corda.v5.base.util.toHex
import org.apache.commons.validator.routines.InetAddressValidator
import org.bouncycastle.asn1.x500.AttributeTypeAndValue
import org.bouncycastle.asn1.x500.X500Name

object SniCalculator {

    private const val HASH_ALGO = "SHA-256"
    const val HASH_TRUNCATION_SIZE = 32
    const val CLASSIC_CORDA_SNI_SUFFIX = ".p2p.corda.net" //This is intentionally different to Corda 4
    const val IP_SNI_SUFFIX = ".cluster.corda.net"

    fun calculateCorda4Sni(
        x500Name: String,
    ): String {
        val x500NameSorted = X500Name(x500Name).rdNs.flatMap { it.typesAndValues.asList() }.sortedBy { it.type.toString() }.groupBy(
            AttributeTypeAndValue::getType, AttributeTypeAndValue::getValue)
            .mapValues { it.value[0].toString() }.map { it }.joinToString(", ")
        return sha256Hash(x500NameSorted.toByteArray()).toHex().take(HASH_TRUNCATION_SIZE)
            .lowercase() + CLASSIC_CORDA_SNI_SUFFIX
    }

    fun calculateCorda5Sni(
        address: URI,
    ): String {
        val host = address.host
        return if (InetAddressValidator.getInstance().isValid(host)) {
            "$host$IP_SNI_SUFFIX"
        } else {
            host
        }
    }

    private fun sha256Hash(bytes: ByteArray): ByteArray {
        val provider = BouncyCastleProvider()
        val messageDigest = MessageDigest.getInstance(HASH_ALGO, provider)
        messageDigest.reset()
        messageDigest.update(bytes)
        return messageDigest.digest()
    }
}

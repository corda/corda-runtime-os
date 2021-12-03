package net.corda.p2p.linkmanager.utilities

import net.corda.v5.base.util.toHex
import org.bouncycastle.asn1.x500.AttributeTypeAndValue
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest

object SourceCalculator {

    fun calculateSource(
        x500Name: String
    ): String {
        val x500NameSorted =
            X500Name(x500Name).rdNs.flatMap { it.typesAndValues.asList() }.sortedBy { it.type.toString() }
                .groupBy(AttributeTypeAndValue::getType, AttributeTypeAndValue::getValue)
                .mapValues { it.value[0].toString() }.map { it }.joinToString(", ")
        return x500NameSorted
    }
}
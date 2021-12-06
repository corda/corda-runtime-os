package net.corda.p2p.linkmanager.utilities

import org.bouncycastle.asn1.x500.AttributeTypeAndValue
import org.bouncycastle.asn1.x500.X500Name

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
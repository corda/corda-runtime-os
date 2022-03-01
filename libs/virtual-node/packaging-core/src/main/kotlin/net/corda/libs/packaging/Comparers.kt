package net.corda.libs.packaging

import net.corda.packaging.VersionComparator
import net.corda.v5.crypto.SecureHash
import java.util.Arrays

internal val secureHashComparator = Comparator.nullsFirst(
    Comparator.comparing(SecureHash::algorithm)
        .then { h1, h2 -> Arrays.compare(h1?.bytes, h2?.bytes) })

interface Identifier {
    val name: String
    val version: String
    val signerSummaryHash: SecureHash?
}

internal val identifierComparator = Comparator.comparing(Identifier::name)
    .thenComparing(Identifier::version, VersionComparator())
    .thenComparing(Identifier::signerSummaryHash, secureHashComparator)
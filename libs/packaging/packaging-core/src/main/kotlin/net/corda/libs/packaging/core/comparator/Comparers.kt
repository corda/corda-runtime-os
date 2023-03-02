package net.corda.libs.packaging.core.comparator

import net.corda.libs.packaging.core.Identifier
import net.corda.v5.crypto.SecureHash
import java.util.Arrays

internal val secureHashComparator = Comparator.nullsFirst(
    Comparator.comparing(SecureHash::getAlgorithm)
        .then { h1, h2 -> Arrays.compare(h1?.bytes, h2?.bytes) })

internal val identifierComparator = Comparator.comparing(Identifier::name)
    .thenComparing(Identifier::version, VersionComparator())
    .thenComparing(Identifier::signerSummaryHash, secureHashComparator)
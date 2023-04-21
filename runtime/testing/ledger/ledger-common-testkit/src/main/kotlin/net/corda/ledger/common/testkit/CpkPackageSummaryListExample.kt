package net.corda.ledger.common.testkit

import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl

fun cpkPackageSummaryListExample(seed: String? = "123CBA") = List(3) {
    CordaPackageSummaryImpl(
        "$seed-cpk$it",
        "signerSummaryHash$it",
        "$it.0",
        "$seed-fileChecksum$it"
    )
}

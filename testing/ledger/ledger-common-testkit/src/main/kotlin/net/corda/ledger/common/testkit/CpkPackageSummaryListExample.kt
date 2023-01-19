package net.corda.ledger.common.testkit

import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl

fun cpkPackageSummaryListExample(seed: String? = "123CBA") = listOf(
    CordaPackageSummaryImpl(
        "$seed-cpk1",
        "signerSummaryHash1",
        "1.0",
        "$seed-fileChecksum1"
    ),
    CordaPackageSummaryImpl(
        "$seed-cpk2",
        "signerSummaryHash2",
        "2.0",
        "$seed-fileChecksum2"
    ),
    CordaPackageSummaryImpl(
        "$seed-cpk3",
        "signerSummaryHash3",
        "3.0",
        "$seed-fileChecksum3"
    )
)
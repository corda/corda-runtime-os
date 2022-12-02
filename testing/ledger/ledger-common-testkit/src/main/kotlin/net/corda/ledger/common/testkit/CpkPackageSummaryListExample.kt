package net.corda.ledger.common.testkit

import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl

val cpkPackageSummaryListExample = listOf(
    CordaPackageSummaryImpl(
        "MockCpk",
        "1",
        "",
        "0101010101010101010101010101010101010101010101010101010101010101"),
    CordaPackageSummaryImpl(
        "MockCpk",
        "3",
        "",
        "0303030303030303030303030303030303030303030303030303030303030303")
)
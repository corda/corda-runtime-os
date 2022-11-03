package net.corda.ledger.common.testkit

import net.corda.ledger.common.data.transaction.CordaPackageSummary

val cpkPackageSummaryListExample = listOf(
    CordaPackageSummary(
        "MockCpk",
        "1",
        "",
        "0101010101010101010101010101010101010101010101010101010101010101"),
    CordaPackageSummary(
        "MockCpk",
        "3",
        "",
        "0303030303030303030303030303030303030303030303030303030303030303")
)
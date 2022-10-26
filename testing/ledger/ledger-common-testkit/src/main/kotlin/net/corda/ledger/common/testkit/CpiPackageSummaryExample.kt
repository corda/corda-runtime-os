package net.corda.ledger.common.testkit

import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.v5.crypto.SecureHash

val cpiPackgeSummaryExample = CordaPackageSummary(
    name = "CPI name",
    version = "CPI version",
    signerSummaryHash = SecureHash("SHA-256", "Fake-value".toByteArray()).toHexString(),
    fileChecksum = SecureHash("SHA-256", "Another-Fake-value".toByteArray()).toHexString()
)
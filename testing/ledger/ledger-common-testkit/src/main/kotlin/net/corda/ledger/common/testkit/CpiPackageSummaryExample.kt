package net.corda.ledger.common.testkit

import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.v5.crypto.SecureHash

val cpiPackageSummaryExample = CordaPackageSummaryImpl(
    name = "CPI name",
    version = "1",
    signerSummaryHash = SecureHash("SHA-256", "Fake-value".toByteArray()).toHexString(),
    fileChecksum = SecureHash("SHA-256", "Another-Fake-value".toByteArray()).toHexString()
)
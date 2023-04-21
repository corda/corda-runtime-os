package net.corda.ledger.common.testkit

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl

val cpiPackageSummaryExample = CordaPackageSummaryImpl(
    name = "CPI name",
    version = "1",
    signerSummaryHash = SecureHashImpl("SHA-256", "Fake-value".toByteArray()).toHexString(),
    fileChecksum = SecureHashImpl("SHA-256", "Another-Fake-value".toByteArray()).toHexString()
)
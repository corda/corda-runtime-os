package net.corda.ledger.verification.processor

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.common.transaction.PrivacySalt

interface UtxoTransactionReader {

    val id: SecureHash

    val account: String

    val privacySalt: PrivacySalt

    val rawGroupLists: List<List<ByteArray>>

    val signatures: List<DigitalSignatureAndMetadata>

    val cpkMetadata: List<CordaPackageSummary>
}

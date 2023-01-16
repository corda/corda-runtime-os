package net.corda.ledger.verification.processor.impl

import net.corda.ledger.verification.processor.UtxoTransactionReader
import net.corda.ledger.verification.processor.VerificationService
import net.corda.utilities.time.Clock
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.serialization.SerializationService

class VerificationServiceImpl constructor(
    private val serializationService: SerializationService,
    private val sandboxDigestService: DigestService,
    private val utcClock: Clock
) : VerificationService {

    override fun verifyContracts(transaction: UtxoTransactionReader): Boolean {
        // TODO Move contract verification code from flow processor here
        return true
    }
}

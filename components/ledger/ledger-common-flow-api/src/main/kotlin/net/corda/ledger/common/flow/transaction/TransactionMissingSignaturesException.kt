package net.corda.ledger.common.flow.transaction

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionVerificationException
import java.security.PublicKey

class TransactionMissingSignaturesException(
    id: SecureHash,
    val missingSignatories: Set<PublicKey>,
    message: String
) : TransactionVerificationException(id, message, null)
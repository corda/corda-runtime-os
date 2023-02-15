package net.corda.ledger.common.flow.transaction

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import java.security.PublicKey

class TransactionSignatureMissingSignaturesException(
    id: SecureHash,
    val missingSignatories: Set<PublicKey>,
    message: String
) : TransactionSignatureException(id, message, null)
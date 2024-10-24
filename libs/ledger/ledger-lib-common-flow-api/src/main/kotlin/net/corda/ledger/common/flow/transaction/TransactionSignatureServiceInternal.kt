package net.corda.ledger.common.flow.transaction

import net.corda.v5.ledger.common.transaction.TransactionSignatureService

interface TransactionSignatureServiceInternal :
    TransactionSignatureService,
    TransactionSignatureVerificationServiceInternal

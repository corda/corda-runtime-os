package com.r3.corda.notary.plugin.common

import net.corda.v5.ledger.common.transaction.TransactionSignatureService

interface TransactionSignatureServiceInternal : TransactionSignatureService,
    TransactionSignatureVerificationServiceInternal
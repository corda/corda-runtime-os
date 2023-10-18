package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import java.math.BigDecimal

@CordaSerializable
data class Payment(
    val applicationName : String,
    val toReserve : BigDecimal
)

@CordaSerializable
data class DraftTx(
    val applicationName : String,
    val recipientOnOtherLedger: String,
    val draftTxId : SecureHash,
    val notaryKey : ByteArray
)

package net.cordapp.demo.utxo.messages

import net.corda.v5.crypto.SecureHash

data class UpdateObligationResponseMessage(val transactionId: SecureHash)

package net.cordapp.demo.obligation.messages

import net.corda.v5.crypto.SecureHash

data class DeleteObligationResponseMessage(val transactionId: SecureHash)

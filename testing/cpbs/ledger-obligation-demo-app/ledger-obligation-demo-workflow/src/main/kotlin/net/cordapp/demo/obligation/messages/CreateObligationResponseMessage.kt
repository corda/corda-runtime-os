package net.cordapp.demo.obligation.messages

import net.corda.v5.crypto.SecureHash
import java.util.UUID

data class CreateObligationResponseMessage(val transactionId: SecureHash, val obligationId: UUID)

package net.cordapp.demo.obligation.messages

import java.util.UUID

data class CreateObligationResponseMessage(val transactionId: String, val obligationId: UUID)

package net.cordapp.demo.obligation.messages

import java.util.*

/**
 * Represents a response from a created obligation.
 *
 * @property transactionId The ID of the transaction in which the obligation was created.
 * @property obligationId The ID of the created obligation.
 */
data class CreateObligationResponseMessage(val transactionId: String, val obligationId: UUID)

package net.cordapp.demo.obligation.messages

/**
 * Represents a response from a deleted obligation.
 *
 * @property transactionId The ID of the transaction in which the obligation was deleted.
 */
data class DeleteObligationResponseMessage(val transactionId: String)

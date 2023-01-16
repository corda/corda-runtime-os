package net.cordapp.demo.obligation.messages

/**
 * Represents a response from an updated obligation.
 *
 * @property transactionId The ID of the transaction in which the obligation was updated.
 */
data class UpdateObligationResponseMessage(val transactionId: String)

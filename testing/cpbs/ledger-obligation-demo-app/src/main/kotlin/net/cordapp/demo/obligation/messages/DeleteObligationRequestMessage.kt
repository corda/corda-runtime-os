package net.cordapp.demo.obligation.messages

import java.util.*

/**
 * Represents a request to delete an obligation.
 *
 * @property id The ID of the obligation to delete.
 */
data class DeleteObligationRequestMessage(val id: UUID)

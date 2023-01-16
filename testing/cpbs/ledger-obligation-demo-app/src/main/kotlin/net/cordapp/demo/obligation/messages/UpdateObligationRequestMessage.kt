package net.cordapp.demo.obligation.messages

import java.math.BigDecimal
import java.util.UUID

/**
 * Represents a request to update an obligation.
 *
 * @property id The ID of the obligation to be updated.
 * @property amountToSettle The amount to settle from the specified obligation.
 * @property doubleSpend TODO
 */
data class UpdateObligationRequestMessage(
    val id: UUID,
    val amountToSettle: BigDecimal,
    // For testing
    val doubleSpend: Boolean = false
)

package net.cordapp.demo.obligation.messages

import net.corda.v5.base.types.MemberX500Name
import java.math.BigDecimal

/**
 * Represents a request to create an obligation.
 *
 * @property creditor The participant who is owed the obligation.
 * @property debtor The participant who owes the obligation.
 * @property amount The amount owed.
 * @property notary TODO
 * @property notaryService TODO
 * @property fromDayOffset TODO
 * @property toDayOffset TODO
 */
data class CreateObligationRequestMessage(
    val debtor: MemberX500Name,
    val creditor: MemberX500Name,
    val amount: BigDecimal,
    val notary: MemberX500Name,
    val notaryService: MemberX500Name,
    // For testing
    val fromDayOffset: Int = 0,
    val toDayOffset: Int = 1,
    // These will be used like this:
    /*
    .setTimeWindowBetween(
        Instant.now().plusMillis(fromDayOffset.days.toMillis()),
        Instant.now().plusMillis(toDayOffset.days.toMillis())
    )
     */
)

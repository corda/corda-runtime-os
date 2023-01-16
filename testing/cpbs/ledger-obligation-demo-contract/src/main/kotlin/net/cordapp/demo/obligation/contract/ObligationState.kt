package net.cordapp.demo.obligation.contract

import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.math.BigDecimal
import java.security.PublicKey
import java.util.*

/**
 * Represents a basic obligation.
 *
 * The intention of this obligation is to represent an IOU (I-owe-you) type obligation, similar to a promissory note.
 *
 *
 * @property creditor The participant who is owed the obligation.
 * @property debtor The participant who owes the obligation.
 * @property amount The amount owed.
 * @property id The unique ID of the obligation.
 * @property participants The public keys of any participants associated with the current contract state.
 */
@BelongsToContract(ObligationContract::class)
data class ObligationState(
    val creditor: PublicKey,
    val debtor: PublicKey,
    val amount: BigDecimal,
    val id: UUID = UUID.randomUUID()
) : ContractState {

    override val participants: List<PublicKey>
        get() = listOf(creditor, debtor).distinct()

    /**
     * Settles an amount from the current obligation.
     *
     * @param amountToSettle The amount to settle from the current obligation.
     * @return Returns a new [ObligationState] less the settled amount.
     */
    fun settle(amountToSettle: BigDecimal): ObligationState {
        return copy(amount = amount - amountToSettle)
    }
}

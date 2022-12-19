package net.cordapp.demo.obligation.contract

import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.math.BigDecimal
import java.security.PublicKey
import java.util.UUID

@BelongsToContract(ObligationContract::class)
data class ObligationState(
    val issuer: PublicKey,
    val holder: PublicKey,
    val amount: BigDecimal,
    val id: UUID = UUID.randomUUID()
) : ContractState {

    override val participants: List<PublicKey>
        get() = listOf(issuer, holder).distinct()

    fun settle(amountToSettle: BigDecimal): ObligationState {
        return copy(amount = amount - amountToSettle)
    }
}

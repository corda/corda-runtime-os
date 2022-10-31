package net.cordapp.demo.utxo.contract

import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.math.BigDecimal
import java.security.PublicKey

@BelongsToContract(ObligationContract::class)
data class ObligationState(val issuer: PublicKey, val holder: PublicKey, val amount: BigDecimal) : ContractState {
    override val participants: List<PublicKey> get() = listOf(issuer, holder).distinct()
}

package net.cordapp.demo.utxo.token

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.math.BigDecimal
import java.security.PublicKey

@Suppress("LongParameterList")
@BelongsToContract(CoinContract::class)
class CoinState(
    val issuer: SecureHash,
    val currency: String,
    val owner: PublicKey,
    val value: BigDecimal,
    override val participants: List<PublicKey>,
    val tag: String? = null,
    val ownerHash: SecureHash? = null
) : ContractState {
    companion object {
        val tokenType = CoinState::class.java.name.toString()
    }

    override fun toString(): String {
        return  "issuer: $issuer, " +
                "currency: $currency, " +
                "issuer: $issuer, " +
                "owner: $owner, " +
                "value: $value, " +
                "participants: $participants, " +
                "tag: $tag, " +
                "ownerHash: $ownerHash, "
    }
}
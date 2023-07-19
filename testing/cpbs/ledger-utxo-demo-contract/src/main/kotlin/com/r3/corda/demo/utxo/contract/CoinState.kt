package com.r3.corda.demo.utxo.contract

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
    val value: BigDecimal,
    private val participants: List<PublicKey>,
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
                "value: $value, " +
                "participants: $participants, " +
                "tag: $tag, " +
                "ownerHash: $ownerHash, "
    }

    override fun getParticipants(): List<PublicKey> {
        return participants
    }
}
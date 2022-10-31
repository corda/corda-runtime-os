package net.corda.v5.ledger.utxo.observer

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.ContractState
import java.math.BigDecimal
import java.security.PublicKey

data class ExampleStateK(
    override val participants: List<PublicKey>,
    val issuer: SecureHash,
    val currency: String,
    val amount: BigDecimal
) : ContractState
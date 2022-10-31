package net.corda.v5.ledger.utxo.observer

import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import java.math.BigDecimal

/**
 * The [UtxoToken] defines the structure of a token produced by an instance of [UtxoLedgerTokenStateObserver]
 *
 * @property poolKey The key of the token pool this token belongs to.
 * @property amount The amount represented by this token.
 * @property filterFields Optional fields available to the [TokenSelection] API [TokenClaimCriteria].
 */
data class UtxoToken(
    val poolKey: UtxoTokenPoolKey,
    val amount: BigDecimal,
    val filterFields: UtxoTokenFilterFields?
)
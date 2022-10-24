package net.corda.v5.ledger.utxo.token.selection

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import java.math.BigDecimal

/**
 * [TokenClaimCriteria] describes the selection criteria for a token selection query using the [TokenSelection] API.
 *
 * @property tokenType The type of tokens to be selected.
 * @property issuerHash The [SecureHash] of issuer of tokens to be selected.
 * @property notaryX500Name The [MemberX500Name] of the notary of tokens to be selected.
 * @property symbol The symbol of the notary of tokens to be selected.
 * @property targetAmount The minimum value for the sum of [ClaimedToken.amount] for the selected tokens.
 * @property tagRegex Optional regular expression to match against the [ClaimedToken.tag] field. Null matches all.
 * @property ownerHash Optional owner [SecureHash] of the tokens to be selected . Null matches all.
 */
class TokenClaimCriteria(
    val tokenType: String,
    val issuerHash: SecureHash,
    val notaryX500Name: MemberX500Name,
    val symbol: String,
    val targetAmount: BigDecimal
) {
    var tagRegex: String? = null
    var ownerHash: SecureHash? = null
}


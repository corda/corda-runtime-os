package net.corda.v5.ledger.contracts

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.identity.PartyAndReference

/**
 * The [Issued] data class holds the details of an on ledger digital asset.
 * In particular, it gives the public credentials of the entity that created these digital tokens
 * and the particular product represented.
 *
 * @param P the class type of product underlying the definition, for example [java.util.Currency].
 * @property issuer The [AbstractParty][net.corda.v5.application.identity.AbstractParty] details of the entity
 * which issued the asset and a reference blob, which can contain other details related to the token creation
 * e.g. serial number, warehouse location, etc.
 * The issuer is the gatekeeper for creating, or destroying the tokens on the digital ledger and
 * only their [PrivateKey][java.security.PrivateKey] signature can authorise transactions that do not conserve the total number
 * of tokens on the ledger.
 * Other identities may own the tokens, but they can only create transactions that conserve the total token count.
 * Typically, the issuer is also a well-know organisation that can convert digital tokens to external assets
 * and thus underwrites the digital tokens.
 * Different issuer values may coexist for a particular product, but these cannot be merged.
 * @property product The details of the specific product represented by these digital tokens. The value
 * of product may differentiate different kinds of asset within the same logical class e.g the currency, or
 * it may just be a type marker for a single custom asset.
 */
@CordaSerializable
data class Issued<out P : Any>(val issuer: PartyAndReference, val product: P) {
    init {
        require(issuer.reference.size <= MAX_ISSUER_REF_SIZE) { "Maximum issuer reference size is $MAX_ISSUER_REF_SIZE." }
    }

    override fun toString() = "$product issued by $issuer"
}

/**
 * The maximum permissible size of an issuer reference.
 */
const val MAX_ISSUER_REF_SIZE = 1024

/**
 * Strips the issuer and returns an [Amount] of the raw token directly. This is useful when you are mixing code that
 * cares about specific issuers with code that will accept any, or which is imposing issuer constraints via some
 * other mechanism and the additional type safety is not wanted.
 */
fun <T : Any> Amount<Issued<T>>.withoutIssuer(): Amount<T> = Amount(quantity, displayTokenSize, token.product)
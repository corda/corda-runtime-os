package net.cordapp.testing.testflows.messages

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.token.selection.ClaimedToken
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.UUID

/**
 * HACK: This class has been added for testing will be removed by CORE-5722 (ledger integration)
 */
class CreateTokenRequest {
    var tokenType: String? = null
    var issuerHash: String? = null
    var notaryX500Name: String? = null
    var symbol: String? = null
    var amount: Long? = null
    var tag: String? = null
    var ownerHash: String? = null

    fun getClaimedToken(): ClaimedToken {
        return ClaimedTokenImpl(this)
    }

    private class ClaimedTokenImpl(private val request: CreateTokenRequest) : ClaimedToken {

        private fun UUID.toStateRef(): StateRef {
            val algorithm = DigestAlgorithmName.SHA2_256.name
            val txId = SecureHash(
                algorithm = algorithm,
                bytes = MessageDigest.getInstance(algorithm).digest(this.toString().toByteArray())
            )

            return StateRef(txId, 1)
        }

        override fun getStateRef(): StateRef {
            return UUID.randomUUID().toStateRef()
        }

        override fun getTokenType(): String {
            return request.tokenType!!
        }

        override fun getIssuerHash(): SecureHash {
            return SecureHash.parse(request.issuerHash!!)
        }

        override fun getNotaryX500Name(): MemberX500Name {
            return MemberX500Name.parse(request.notaryX500Name!!)
        }

        override fun getSymbol(): String {
            return request.symbol!!
        }

        override fun getTag(): String? {
            return request.tag
        }

        override fun getOwnerHash(): SecureHash? {
            return request.ownerHash?.let { SecureHash.parse(request.ownerHash!!) }
        }

        override fun getAmount(): BigDecimal {
            return BigDecimal(request.amount ?: 0L)
        }
    }
}

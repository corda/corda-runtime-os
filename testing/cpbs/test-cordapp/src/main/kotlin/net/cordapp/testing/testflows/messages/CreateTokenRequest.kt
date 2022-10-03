package net.cordapp.testing.testflows.messages

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.token.selection.ClaimedToken
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.*

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
        override val amount: BigDecimal
            get() = BigDecimal(request.amount ?: 0L)

        override val issuerHash: SecureHash
            get() = SecureHash.parse(request.issuerHash!!)

        override val notaryX500Name: MemberX500Name
            get() = MemberX500Name.parse(request.notaryX500Name!!)

        override var ownerHash: SecureHash?
            get() = request.ownerHash?.let { SecureHash.parse(request.ownerHash!!) }

            @Suppress("UNUSED_PARAMETER")
            set(value) {
                TODO()
            }

        override val stateRef: StateRef
            get() = UUID.randomUUID().toStateRef()

        override val symbol: String
            get() = request.symbol!!

        override var tag: String?
            get() = request.tag

            @Suppress("UNUSED_PARAMETER")
            set(value) {
                TODO()
            }

        override val tokenType: String
            get() = request.tokenType!!

        private fun UUID.toStateRef(): StateRef {
            val algorithm = DigestAlgorithmName.SHA2_256.name
            val txId = SecureHash(
                algorithm = algorithm,
                bytes = MessageDigest.getInstance(algorithm).digest(this.toString().toByteArray())
            )

            return StateRef(txId, 1)
        }
    }
}


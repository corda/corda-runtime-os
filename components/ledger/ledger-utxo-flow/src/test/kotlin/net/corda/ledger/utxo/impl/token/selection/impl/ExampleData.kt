package net.corda.ledger.utxo.impl.token.selection.impl

import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.virtualnode.HoldingIdentity
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest

const val BOB_X500 = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
const val ALICE_X500 = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
val BOB_X500_NAME = MemberX500Name.parse(BOB_X500)
val ALICE_X500_NAME = MemberX500Name.parse(ALICE_X500)
val ALICE_X500_HOLDING_ID = HoldingIdentity(ALICE_X500_NAME, "g1")

fun String.toSecureHash(): SecureHash {
    val algorithm = DigestAlgorithmName.SHA2_256.name
    return SecureHash(
        algorithm = algorithm,
        bytes = MessageDigest.getInstance(algorithm).digest(this.toByteArray())
    )
}

fun String.toStateRef(): StateRef {
    return StateRef(this.toSecureHash(), 1)
}

fun Long.toAmount(): TokenAmount {
    return TokenAmount(0, ByteBuffer.wrap(BigInteger.valueOf(this).toByteArray()))
}

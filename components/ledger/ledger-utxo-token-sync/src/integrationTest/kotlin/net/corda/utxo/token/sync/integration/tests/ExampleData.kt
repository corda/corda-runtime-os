package net.corda.utxo.token.sync.integration.tests

import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*

const val HOLDING_IDENTITY_GROUP = "test-cordapp"

const val BOB_X500 = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
val BOB_X500_NAME = MemberX500Name.parse(BOB_X500)
val BOB_HOLDING_IDENTITY = HoldingIdentity(BOB_X500_NAME, HOLDING_IDENTITY_GROUP)
val BOB_SHORT_ID = BOB_HOLDING_IDENTITY.shortHash.toString()
val BOB_VIRTUAL_NODE = VirtualNodeInfo(
    BOB_HOLDING_IDENTITY,
    CpiIdentifier(
        "TEST",
        "0.0",
        null
    ),
    null,
    UUID.randomUUID(),
    null,
    UUID.randomUUID(),
    null,
    UUID.randomUUID(),
    null,
    timestamp = Instant.now(),
    state = VirtualNodeInfo.DEFAULT_INITIAL_STATE // Leaving as a constant value as this is just for testing
)

const val ALICE_X500 = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
val ALICE_X500_NAME = MemberX500Name.parse(ALICE_X500)
val ALICE_HOLDING_IDENTITY = HoldingIdentity(ALICE_X500_NAME, HOLDING_IDENTITY_GROUP)
val ALICE_VIRTUAL_NODE = VirtualNodeInfo(
    ALICE_HOLDING_IDENTITY,
    CpiIdentifier(
        "TEST",
        "0.0",
        null
    ),
    null,
    UUID.randomUUID(),
    null,
    UUID.randomUUID(),
    null,
    UUID.randomUUID(),
    null,
    timestamp = Instant.now(),
    state = VirtualNodeInfo.DEFAULT_INITIAL_STATE // Leaving as a constant value as this is just for testing
)

fun token(stateRef: String, amount: Long, ownerHash: String? = "o1", tag: String? = "t1"): Token {
    val decimalAmount = BigDecimal(amount)
    val tokenAmount = TokenAmount(decimalAmount.scale(), ByteBuffer.wrap(decimalAmount.unscaledValue().toByteArray()))
    return Token(stateRef, tokenAmount, ownerHash, tag)
}



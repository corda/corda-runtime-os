package net.corda.ledger.consensual.impl

import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator

class TestConsensualState(
    val testField: String,
    override val participants: List<Party>
) : ConsensualState {
    override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TestConsensualState) return false
        if (other.testField != testField) return false
        if (other.participants.size != participants.size) return false
        return other.participants.containsAll(participants)
    }

    override fun hashCode(): Int = testField.hashCode() + participants.hashCode() * 31
}

class ConsensualTransactionMocks {
    companion object {
        private val kpg: KeyPairGenerator = KeyPairGenerator.getInstance("RSA").also {
            it.initialize(512)
        }

        val testMemberX500Name = MemberX500Name("R3", "London", "GB")
        val testPublicKey = kpg.genKeyPair().public
        val testPartyImpl = Party(testMemberX500Name, testPublicKey)
        val testConsensualState = TestConsensualState("test", listOf(testPartyImpl))

        fun mockMemberLookup(): MemberLookup {
            val holdingIdentity = HoldingIdentity(testMemberX500Name, "1")

            val mgmContext = mock<MGMContext> {
                on { parseOrNull(eq(MemberInfoExtension.IS_MGM), any<Class<Boolean>>()) } doReturn true
                on { parse(eq(MemberInfoExtension.STATUS), any<Class<String>>()) } doReturn "fakestatus"
                on { entries } doReturn mapOf("mgm" to holdingIdentity.x500Name.toString()).entries
            }
            val memberContext = mock<MemberContext> {
                on { parse(eq(MemberInfoExtension.GROUP_ID), any<Class<String>>()) } doReturn holdingIdentity.groupId
                on { entries } doReturn mapOf("member" to holdingIdentity.x500Name.toString()).entries
            }
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
                on { memberProvidedContext } doReturn memberContext
                on { platformVersion } doReturn 888
                on { name } doReturn testMemberX500Name
                on { groupId } doReturn "1"
            }
            val memberLookup: MemberLookup = mock()
            whenever(memberLookup.myInfo()).thenReturn(memberInfo)

            return memberLookup
        }

        fun mockSandboxCpks(): List<CpkIdentifier> {
            return listOf(
                CpkIdentifier("MockCpk", "1", null),
                CpkIdentifier("MockCpk", "2", null),
                CpkIdentifier("MockCpk", "3", null),
            )
        }

        fun mockSigningService(): SigningService {
            val signingService: SigningService = mock()
            val signature = DigitalSignature.WithKey(testPublicKey, "0".toByteArray(), mapOf())
            whenever(signingService.sign(any(), any(), any())).thenReturn(signature)
            return signingService
        }
    }
}

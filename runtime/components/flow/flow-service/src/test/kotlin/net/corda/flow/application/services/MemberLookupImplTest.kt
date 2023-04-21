package net.corda.flow.application.services

import net.corda.crypto.cipher.suite.PublicKeyHash
import net.corda.flow.ALICE_X500_NAME
import net.corda.flow.application.services.impl.MemberLookupImpl
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class MemberLookupImplTest {
    private val flowFiberService = MockFlowFiberService()
    private val membershipGroupReader = flowFiberService.flowFiberExecutionContext.membershipGroupReader
    private val virtualNodeX500Name = flowFiberService.flowFiberExecutionContext.memberX500Name

    @Test
    fun `test lookup returns list of members`() {
        val member1 = mock<MemberInfo>()
        val member2 = mock<MemberInfo>()

        val expected = listOf(member1, member2)

        whenever(membershipGroupReader.lookup()).thenReturn(listOf(member1, member2))

        val target = MemberLookupImpl(flowFiberService)

        assertThat(target.lookup()).hasSameElementsAs(expected)
    }

    @Test
    fun `test lookup by public key`() {
        val key = mock<PublicKey>().apply {
            whenever(encoded).thenReturn(ByteArray(32) { 1 })
        }

        val keyHash = PublicKeyHash.calculate(key)
        val member1 = mock<MemberInfo>()

        whenever(membershipGroupReader.lookupByLedgerKey(keyHash)).thenReturn(member1)

        val target = MemberLookupImpl(flowFiberService)

        assertThat(target.lookup(key)).isSameAs(member1)
    }

    @Test
    fun `test lookup by x500 name`() {
        val member1 = mock<MemberInfo>()

        whenever(membershipGroupReader.lookup(ALICE_X500_NAME)).thenReturn(member1)

        val target = MemberLookupImpl(flowFiberService)

        assertThat(target.lookup(ALICE_X500_NAME)).isSameAs(member1)
    }

    @Test
    fun `test lookup by my info uses virtual node x500 name`() {
        val member1 = mock<MemberInfo>()

        whenever(membershipGroupReader.lookup(virtualNodeX500Name)).thenReturn(member1)

        val target = MemberLookupImpl(flowFiberService)

        assertThat(target.myInfo()).isSameAs(member1)
    }
}

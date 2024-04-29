package net.corda.virtualnode.write.db.impl.tests.writer.asyncoperation.utility

import net.corda.data.membership.PersistentMemberInfo
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.utility.MgmInfoPersistenceHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MgmInfoPersistenceHelperTest {
    private companion object {
        val TOPIC = "dummyTopic"
        val KEY = "dummyKey"
    }
    private val viewOwner = mock<HoldingIdentity>()
    private val mgmInfo = mock<PersistentMemberInfo>()
    private val mgmSelfSignedInfo = mock<SelfSignedMemberInfo>()
    private val records = listOf(Record(TOPIC, KEY, mgmInfo))
    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on { persistMemberInfo(eq(viewOwner), eq(listOf(mgmSelfSignedInfo))) } doReturn mock()
    }
    private val memberInfoFactory = mock<MemberInfoFactory> {
        on { createMgmSelfSignedMemberInfo(eq(mgmInfo)) } doReturn mgmSelfSignedInfo
    }
    private val mgmInfoPersistenceHelper = MgmInfoPersistenceHelper(membershipPersistenceClient, memberInfoFactory)

    @Test
    fun `persisting mgm info succeeds`() {
        assertDoesNotThrow {
            mgmInfoPersistenceHelper.persistMgmMemberInfo(viewOwner, records)
        }
    }

    @Test
    fun `throws exception when persisting member info fails`() {
        val operation = mock<MembershipPersistenceOperation<Unit>> {
            on { execute() } doReturn MembershipPersistenceResult.Failure("error")
        }
        whenever(membershipPersistenceClient.persistMemberInfo(eq(viewOwner), eq(listOf(mgmSelfSignedInfo))))
            .thenReturn(operation)
        val ex = assertThrows<CordaRuntimeException> {
            mgmInfoPersistenceHelper.persistMgmMemberInfo(viewOwner, records)
        }
        assertThat(ex).hasMessageContaining("Persisting of MGM information failed.")
    }

    @Test
    fun `throws exception when record is not PersistentMemberInfo`() {
        val records = listOf(Record(TOPIC, KEY, "dummyValue"))
        val ex = assertThrows<CordaRuntimeException> {
            mgmInfoPersistenceHelper.persistMgmMemberInfo(viewOwner, records)
        }
        assertThat(ex).hasMessageContaining("Could not find MGM information to persist.")
    }

    @Test
    fun `retries and eventually fails when virtual node info can't be retrieved`() {
        val operation = mock<MembershipPersistenceOperation<Unit>> {
            on { execute() } doReturn MembershipPersistenceResult.Failure("Virtual node info can't be retrieved")
        }
        whenever(membershipPersistenceClient.persistMemberInfo(eq(viewOwner), eq(listOf(mgmSelfSignedInfo))))
            .thenReturn(operation)
        val ex = assertThrows<CordaRuntimeException> {
            mgmInfoPersistenceHelper.persistMgmMemberInfo(viewOwner, records)
        }
        verify(membershipPersistenceClient, times(6))
            .persistMemberInfo(eq(viewOwner), eq(listOf(mgmSelfSignedInfo)))
        assertThat(ex).hasMessageContaining("Persisting of MGM information failed.")
    }
}

package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedGroupParameters
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.ActivateMember
import net.corda.data.membership.db.response.command.ActivateMemberResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.InvalidEntityUpdateException
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.test.util.time.TestClock
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.PessimisticLockException

class ActivateMemberHandlerTest {

    private companion object {
        const val SERIAL_NUMBER = 5L
        const val REASON = "test"
    }

    private val knownGroupId = UUID(0, 1).toString()
    private val knownX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val holdingIdentity = HoldingIdentity(knownX500Name, knownGroupId)
    private val ourVirtualNodeInfo: VirtualNodeInfo = mock {
        on { vaultDmlConnectionId } doReturn UUID(0, 1)
    }
    private val clock: Clock = TestClock(Instant.ofEpochSecond(1))

    private val transaction: EntityTransaction = mock()
    private val em: EntityManager = mock {
        on { transaction } doReturn transaction
    }
    private val emf: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn em
    }
    private val dbConnectionManager: DbConnectionManager = mock {
        on { getOrCreateEntityManagerFactory(any<UUID>(), any(), eq(false)) } doReturn emf
    }
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getByHoldingIdentityShortHash(holdingIdentity.shortHash) } doReturn ourVirtualNodeInfo
    }
    private val jpaEntitiesRegistry: JpaEntitiesRegistry = mock {
        on { get(any()) } doReturn mock()
    }
    private val serializedMgmContext = "1234".toByteArray()
    private val memberInfoEntity = mock<MemberInfoEntity> {
        on { mgmContext } doReturn serializedMgmContext
    }
    private val mgmContext = KeyValuePairList(
        listOf(
            KeyValuePair("one", "1")
        )
    )
    private val persistentMemberInfo = mock<PersistentMemberInfo>()
    private val suspensionActivationEntityOperations = mock<SuspensionActivationEntityOperations> {
        on {
            findMember(
                em,
                holdingIdentity.x500Name.toString(),
                holdingIdentity.groupId,
                SERIAL_NUMBER,
                MEMBER_STATUS_SUSPENDED
            )
        } doReturn memberInfoEntity
        on {
            updateStatus(
                em,
                holdingIdentity.x500Name.toString(),
                holdingIdentity.toAvro(),
                memberInfoEntity,
                mgmContext,
                MEMBER_STATUS_ACTIVE
            )
        } doReturn persistentMemberInfo
    }

    private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(any()) } doReturn byteArrayOf(0)
    }
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(serializedMgmContext) } doReturn mgmContext
    }
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on {
            createAvroDeserializer(
                any(),
                eq(KeyValuePairList::class.java)
            )
        } doReturn keyValuePairListDeserializer
        on {
            createAvroSerializer<KeyValuePairList>(any())
        } doReturn keyValuePairListSerializer
    }
    private val memberInfo = mock<MemberInfo> {
        on { memberProvidedContext } doReturn mock()
    }
    private val memberInfoFactory = mock<MemberInfoFactory> {
        on { createMemberInfo(persistentMemberInfo) } doReturn memberInfo
    }
    private val persistenceHandlerServices: PersistenceHandlerServices = mock {
        on { clock } doReturn clock
        on { virtualNodeInfoReadService } doReturn virtualNodeInfoReadService
        on { dbConnectionManager } doReturn dbConnectionManager
        on { jpaEntitiesRegistry } doReturn jpaEntitiesRegistry
        on { cordaAvroSerializationFactory } doReturn cordaAvroSerializationFactory
        on { memberInfoFactory } doReturn memberInfoFactory
        on { transactionTimerFactory } doReturn { transactionTimer }
    }

    private val addNotaryToGroupParametersHandler = mock<AddNotaryToGroupParametersHandler>()
    private val handler: ActivateMemberHandler = ActivateMemberHandler(
        persistenceHandlerServices,
        addNotaryToGroupParametersHandler
    ) { _, _ -> suspensionActivationEntityOperations }
    private val context = MembershipRequestContext(
        clock.instant(),
        UUID(0, 1).toString(),
        holdingIdentity.toAvro()
    )
    private val request = ActivateMember(knownX500Name.toString(), SERIAL_NUMBER, REASON)

    private fun invokeTestFunction(): ActivateMemberResponse {
        return handler.invoke(context, request)
    }

    @Test
    fun `invoke returns the correct data when member is not a notary`() {
        val result = invokeTestFunction()

        verify(addNotaryToGroupParametersHandler, never()).addNotaryToGroupParameters(any(), any())
        assertThat(result.memberInfo).isEqualTo(persistentMemberInfo)
        assertThat(result.groupParameters).isNull()
    }

    @Test
    fun `invoke returns the correct data when member is a notary`() {
        val mockMemberContext = mock<MemberContext> {
            on { entries } doReturn mapOf(
                "${MemberInfoExtension.ROLES_PREFIX}.0" to MemberInfoExtension.NOTARY_ROLE
            ).entries
        }
        val mockMemberInfo = mock<MemberInfo> {
            on { memberProvidedContext } doReturn mockMemberContext
        }
        whenever(memberInfoFactory.createMemberInfo(persistentMemberInfo)).thenReturn(mockMemberInfo)
        val groupParameters = mock<SignedGroupParameters>()
        whenever(addNotaryToGroupParametersHandler.addNotaryToGroupParameters(em, persistentMemberInfo)).doReturn(groupParameters)

        val result = invokeTestFunction()

        assertThat(result.memberInfo).isEqualTo(persistentMemberInfo)
        assertThat(result.groupParameters).isEqualTo(groupParameters)
    }

    @Test
    fun `invoke throws a MembershipPersistenceException if the mgmContext can't be deserialized`() {
        whenever(keyValuePairListDeserializer.deserialize(serializedMgmContext)).thenReturn(null)
        assertThrows<MembershipPersistenceException> { invokeTestFunction() }.also {
            assertThat(it).hasMessageContaining("Failed to deserialize")
        }
    }

    @Test
    fun `when updateGroupParameters throws a PessimisticLockException an InvalidEntityUpdateException is thrown`() {
        val notaryMemberProvidedContext = mock<MemberContext> {
            on { entries } doReturn mapOf("$ROLES_PREFIX.1" to NOTARY_ROLE).entries
        }
        whenever(memberInfo.memberProvidedContext).doReturn(notaryMemberProvidedContext)
        whenever(addNotaryToGroupParametersHandler.addNotaryToGroupParameters(any(), any())).doThrow(
            PessimisticLockException()
        )

        assertThrows<InvalidEntityUpdateException> {
            invokeTestFunction()
        }
    }
}

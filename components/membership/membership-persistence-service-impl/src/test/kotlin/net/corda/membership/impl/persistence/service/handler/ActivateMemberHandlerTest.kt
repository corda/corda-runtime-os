package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.ActivateMember
import net.corda.data.membership.db.response.command.ActivateMemberResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.test.util.time.TestClock
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType

class ActivateMemberHandlerTest {

    private companion object {
        const val SERIAL_NUMBER = 5L
        const val REASON = "test"
    }

    private val knownGroupId = UUID(0, 1).toString()
    private val knownX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val primaryKey = MemberInfoEntityPrimaryKey(
        groupId = knownGroupId,
        memberX500Name = knownX500Name.toString(),
        false
    )
    private val contextBytes = byteArrayOf(1, 10)
    private val memberInfoEntity = mock<MemberInfoEntity> {
        on { mgmContext } doReturn contextBytes
        on { memberContext } doReturn contextBytes
        on { groupId } doReturn knownGroupId
        on { memberX500Name } doReturn knownX500Name.toString()
        on { serialNumber } doReturn SERIAL_NUMBER
        on { status } doReturn MEMBER_STATUS_SUSPENDED
    }
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
        on { createEntityManagerFactory(any(), any()) } doReturn emf
    }
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getByHoldingIdentityShortHash(holdingIdentity.shortHash) } doReturn ourVirtualNodeInfo
    }
    private val jpaEntitiesRegistry: JpaEntitiesRegistry = mock {
        on { get(any()) } doReturn mock()
    }
    private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(any()) } doReturn byteArrayOf(0)
    }
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>>()
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
    private val persistenceHandlerServices: PersistenceHandlerServices = mock {
        on { clock } doReturn clock
        on { virtualNodeInfoReadService } doReturn virtualNodeInfoReadService
        on { dbConnectionManager } doReturn dbConnectionManager
        on { jpaEntitiesRegistry } doReturn jpaEntitiesRegistry
        on { cordaAvroSerializationFactory } doReturn cordaAvroSerializationFactory
    }
    private val handler: ActivateMemberHandler = ActivateMemberHandler(persistenceHandlerServices)
    private val context = MembershipRequestContext(
        clock.instant(),
        UUID(0, 1).toString(),
        holdingIdentity.toAvro()
    )
    private val request = ActivateMember(knownX500Name.toString(), SERIAL_NUMBER, REASON)

    private fun invokeTestFunction(): ActivateMemberResponse {
        return handler.invoke(context, request)
    }

    private fun invokeTestFunctionWithError(errorMsg: String) {
        assertThrows<MembershipPersistenceException> {
            invokeTestFunction()
        }.apply {
            assertThat(message).contains(errorMsg)
        }
    }

    private fun mockMemberInfoEntity(entity: MemberInfoEntity? = memberInfoEntity) {
        whenever(
            em.find(eq(MemberInfoEntity::class.java), eq(primaryKey), eq(LockModeType.OPTIMISTIC_FORCE_INCREMENT))
        ).doReturn(entity)
    }

    private fun mockMemberInfoEntity(
        group: String? = knownGroupId,
        name: MemberX500Name? = knownX500Name,
        serial: Long? = SERIAL_NUMBER,
        memberStatus: String = MEMBER_STATUS_SUSPENDED
    ) {
        val mockEntity = mock<MemberInfoEntity> {
            on { mgmContext } doReturn contextBytes
            on { memberContext } doReturn contextBytes
            on { groupId } doReturn group!!
            on { memberX500Name } doReturn name.toString()
            on { serialNumber } doReturn serial!!
            on { status } doReturn memberStatus
        }
        whenever(
            em.find(eq(MemberInfoEntity::class.java), eq(primaryKey), eq(LockModeType.OPTIMISTIC_FORCE_INCREMENT))
        ).doReturn(mockEntity)
    }

    @Test
    fun `handler can be called successfully and persists correct entity`() {
        whenever(keyValuePairListDeserializer.deserialize(contextBytes)).doReturn(
            KeyValuePairList(
                listOf(
                    KeyValuePair("key", "value"),
                )
            )
        )
        mockMemberInfoEntity()
        val entityCapture = argumentCaptor<MemberInfoEntity>()
        val expectedMgmContext = byteArrayOf(0)
        whenever(keyValuePairListSerializer.serialize(any())).doReturn(expectedMgmContext)
        whenever(em.merge(entityCapture.capture())).doReturn(mock())

        invokeTestFunction()

        with(entityCapture.firstValue) {
            assertThat(status).isEqualTo(MEMBER_STATUS_ACTIVE)
            assertThat(serialNumber).isEqualTo(SERIAL_NUMBER)
            assertThat(modifiedTime).isEqualTo(clock.instant())
            assertThat(groupId).isEqualTo(knownGroupId)
            assertThat(memberX500Name).isEqualTo(knownX500Name.toString())
            assertThat(mgmContext).isEqualTo(expectedMgmContext)
            assertThat(isPending).isFalse
        }
    }

    @Test
    fun `invoke updates MGM-provided context`() {
        whenever(keyValuePairListDeserializer.deserialize(contextBytes)).doReturn(
            KeyValuePairList(
                listOf(
                    KeyValuePair(MemberInfoExtension.STATUS, "value"),
                    KeyValuePair(MemberInfoExtension.MODIFIED_TIME, "value"),
                    KeyValuePair(MemberInfoExtension.SERIAL, "value")
                )
            )
        )
        val mgmContextCapture = argumentCaptor<KeyValuePairList>()
        whenever(keyValuePairListSerializer.serialize(mgmContextCapture.capture())).doReturn(byteArrayOf(0))
        mockMemberInfoEntity()

        invokeTestFunction()

        assertThat(mgmContextCapture.firstValue.items).contains(
            KeyValuePair(MemberInfoExtension.STATUS, MEMBER_STATUS_ACTIVE),
            KeyValuePair(MemberInfoExtension.MODIFIED_TIME, clock.instant().toString()),
            KeyValuePair(MemberInfoExtension.SERIAL, (SERIAL_NUMBER + 1).toString())
        )
    }

    @Test
    fun `invoke returns the correct data`() {
        val memberInfoEntity = mock<MemberInfoEntity> {
            on { memberContext } doReturn byteArrayOf(1)
            on { mgmContext } doReturn byteArrayOf(2)
            on { serialNumber } doReturn SERIAL_NUMBER
            on { status } doReturn MEMBER_STATUS_SUSPENDED
            on { groupId } doReturn knownGroupId
            on { memberX500Name } doReturn knownX500Name.toString()
            on { isPending } doReturn false
        }
        mockMemberInfoEntity(memberInfoEntity)
        val mgmContext = KeyValuePairList(
            listOf(
                KeyValuePair("one", "1")
            )
        )
        val memberContext = KeyValuePairList(
            listOf(
                KeyValuePair("two", "2")
            )
        )
        whenever(keyValuePairListDeserializer.deserialize(byteArrayOf(1))).thenReturn(memberContext)
        whenever(keyValuePairListDeserializer.deserialize(byteArrayOf(2))).thenReturn(mgmContext)

        val result = invokeTestFunction()

        assertThat(result.memberInfo).isEqualTo(
            PersistentMemberInfo(
                context.holdingIdentity,
                memberContext, mgmContext
            )
        )
    }

    @Test
    fun `invoke throws exception if member cannot be found`() {
        mockMemberInfoEntity(entity = null)

        invokeTestFunctionWithError("does not exist")
    }

    @Test
    fun `invoke throws exception if serial number is outdated`() {
        mockMemberInfoEntity(serial = 6L)

        invokeTestFunctionWithError("serial number does not match")
    }

    @Test
    fun `invoke throws exception if member is not currently suspended`() {
        mockMemberInfoEntity(memberStatus = MEMBER_STATUS_ACTIVE)

        invokeTestFunctionWithError("cannot be activated")
    }

    @Test
    fun `invoke throws exception if MGM-provided context cannot be serialized`() {
        whenever(keyValuePairListSerializer.serialize(any())).doReturn(null)
        whenever(keyValuePairListDeserializer.deserialize(contextBytes)).doReturn(
            KeyValuePairList(listOf(KeyValuePair(MemberInfoExtension.STATUS, MEMBER_STATUS_SUSPENDED)))
        )
        mockMemberInfoEntity()

        invokeTestFunctionWithError("Failed to serialize")
    }

    @Test
    fun `invoke throws exception if MGM-provided context cannot be deserialized`() {
        whenever(keyValuePairListDeserializer.deserialize(contextBytes)).doReturn(null)
        mockMemberInfoEntity()

        invokeTestFunctionWithError("Failed to extract")
    }
}

package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.exceptions.InvalidEntityUpdateException
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.test.util.time.TestClock
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions
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

class BaseSuspensionActivationHandlerTest {

    private companion object {
        const val SERIAL_NUMBER = 5L
    }

    private val knownGroupId = UUID(0, 1).toString()
    private val knownX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val primaryKey = MemberInfoEntityPrimaryKey(
        groupId = knownGroupId,
        memberX500Name = knownX500Name.toString(),
        false
    )
    private val contextBytes = byteArrayOf(1, 10)
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

    private val handler = object: BaseSuspensionActivationHandler<String, String>(persistenceHandlerServices) {
        override fun invoke(context: MembershipRequestContext, request: String): String {
            // Do nothing...
            return ""
        }
    }
    private val context = MembershipRequestContext(
        clock.instant(),
        UUID(0, 1).toString(),
        holdingIdentity.toAvro()
    )

    private fun invokeSuspend(): PersistentMemberInfo = with(handler) {
        changeMemberStatus(
            context,
            knownX500Name.toString(),
            SERIAL_NUMBER,
            MEMBER_STATUS_ACTIVE,
            MEMBER_STATUS_SUSPENDED,
        )
    }

    private fun invokeTestFunctionWithError(
        testFunction: () -> PersistentMemberInfo,
        errorMsg: String,
        type: Class<*> = MembershipPersistenceException::class.java
    ) {
        assertThrows<Exception> {
            testFunction.invoke()
        }.apply {
            Assertions.assertThat(this).isInstanceOf(type)
            Assertions.assertThat(message).contains(errorMsg)
        }
    }

    private fun invokeActivate(): PersistentMemberInfo = with(handler) {
        changeMemberStatus(
            context,
            knownX500Name.toString(),
            SERIAL_NUMBER,
            MEMBER_STATUS_SUSPENDED,
            MEMBER_STATUS_ACTIVE,
        )
    }

    @Suppress("LongParameterList")
    private fun mockMemberInfoEntity(
        mgmProvidedContext: ByteArray = contextBytes,
        memberProvidedContext: ByteArray = contextBytes,
        group: String? = knownGroupId,
        name: MemberX500Name? = knownX500Name,
        serial: Long? = SERIAL_NUMBER,
        memberStatus: String = MEMBER_STATUS_SUSPENDED
    ) {
        val mockEntity = mock<MemberInfoEntity> {
            on { mgmContext } doReturn mgmProvidedContext
            on { memberContext } doReturn memberProvidedContext
            on { groupId } doReturn group!!
            on { memberX500Name } doReturn name.toString()
            on { serialNumber } doReturn serial!!
            on { status } doReturn memberStatus
        }
        whenever(
            em.find(eq(MemberInfoEntity::class.java), eq(primaryKey), eq(LockModeType.PESSIMISTIC_WRITE))
        ).doReturn(mockEntity)
    }

    @Test
    fun `changeMemberStatus returns the correct data`() {
        mockMemberInfoEntity(
            mgmProvidedContext = byteArrayOf(1),
            memberProvidedContext = byteArrayOf(2),
            memberStatus = MEMBER_STATUS_ACTIVE
        )
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
        whenever(keyValuePairListDeserializer.deserialize(byteArrayOf(1))).thenReturn(mgmContext)
        whenever(keyValuePairListDeserializer.deserialize(byteArrayOf(2))).thenReturn(memberContext)

        val result = invokeSuspend()

        Assertions.assertThat(result).isEqualTo(
            PersistentMemberInfo(
                context.holdingIdentity,
                memberContext, mgmContext
            )
        )
    }

    @Test
    fun `changeMemberStatus can be called successfully and persists correct entity`() {
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

        invokeActivate()

        with(entityCapture.firstValue) {
            Assertions.assertThat(status).isEqualTo(MEMBER_STATUS_ACTIVE)
            Assertions.assertThat(serialNumber).isEqualTo(SERIAL_NUMBER)
            Assertions.assertThat(modifiedTime).isEqualTo(clock.instant())
            Assertions.assertThat(groupId).isEqualTo(knownGroupId)
            Assertions.assertThat(memberX500Name).isEqualTo(knownX500Name.toString())
            Assertions.assertThat(mgmContext).isEqualTo(expectedMgmContext)
            Assertions.assertThat(isPending).isFalse
        }
    }

    @Test
    fun `changeMemberStatus updates MGM-provided context`() {
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

        invokeActivate()

        Assertions.assertThat(mgmContextCapture.firstValue.items).contains(
            KeyValuePair(MemberInfoExtension.STATUS, MEMBER_STATUS_ACTIVE),
            KeyValuePair(MemberInfoExtension.MODIFIED_TIME, clock.instant().toString()),
            KeyValuePair(MemberInfoExtension.SERIAL, (SERIAL_NUMBER + 1).toString())
        )
    }

    @Test
    fun `changeMemberStatus throws exception if member cannot be found`() {
        whenever(
            em.find(eq(MemberInfoEntity::class.java), eq(primaryKey), eq(LockModeType.PESSIMISTIC_WRITE))
        ).doReturn(null)

        invokeTestFunctionWithError(::invokeSuspend, "does not exist")
    }

    @Test
    fun `changeMemberStatus throws exception if serial number is outdated`() {
        mockMemberInfoEntity(serial = 6L)

        invokeTestFunctionWithError(::invokeSuspend, "serial number does not match", InvalidEntityUpdateException::class.java)
    }

    @Test
    fun `changeMemberStatus throws exception if member is not currently suspended`() {
        mockMemberInfoEntity(memberStatus = MEMBER_STATUS_ACTIVE)

        invokeTestFunctionWithError(::invokeActivate, "cannot be performed", IllegalArgumentException::class.java)

        mockMemberInfoEntity(memberStatus = MemberInfoExtension.MEMBER_STATUS_PENDING)

        invokeTestFunctionWithError(::invokeSuspend, "cannot be performed", IllegalArgumentException::class.java)
    }

    @Test
    fun `changeMemberStatus throws exception if MGM-provided context cannot be serialized`() {
        whenever(keyValuePairListSerializer.serialize(any())).doReturn(null)
        whenever(keyValuePairListDeserializer.deserialize(contextBytes)).doReturn(
            KeyValuePairList(listOf(KeyValuePair(MemberInfoExtension.STATUS,
                MEMBER_STATUS_SUSPENDED
            )))
        )
        mockMemberInfoEntity()

        invokeTestFunctionWithError(::invokeActivate, "Failed to serialize")
    }

    @Test
    fun `changeMemberStatus throws exception if MGM-provided context cannot be deserialized`() {
        whenever(keyValuePairListDeserializer.deserialize(contextBytes)).doReturn(null)
        mockMemberInfoEntity(memberStatus = MEMBER_STATUS_ACTIVE)

        invokeTestFunctionWithError(::invokeSuspend, "Failed to deserialize")
    }
}
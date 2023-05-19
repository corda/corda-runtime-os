package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedContexts
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.SuspendMember
import net.corda.data.membership.db.response.command.SuspendMemberResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoFactory
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType

class SuspendMemberHandlerTest {

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
    private val mgmContextBytes = byteArrayOf(1, 10)
    private val suspendedMgmContextBytes = byteArrayOf(1, 11)
    private val memberContextBytes = byteArrayOf(1, 5)
    val mgmContext = KeyValuePairList(listOf(KeyValuePair("one", "1")))
    val memberContext = KeyValuePairList(listOf(KeyValuePair("two", "2")))
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
        on { serialize(any()) } doReturn suspendedMgmContextBytes
    }
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(mgmContextBytes) } doReturn mgmContext
        on { deserialize(memberContextBytes) } doReturn memberContext
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
    private val memberInfoFactory = mock<MemberInfoFactory>()
    private val persistenceHandlerServices: PersistenceHandlerServices = mock {
        on { clock } doReturn clock
        on { virtualNodeInfoReadService } doReturn virtualNodeInfoReadService
        on { dbConnectionManager } doReturn dbConnectionManager
        on { jpaEntitiesRegistry } doReturn jpaEntitiesRegistry
        on { cordaAvroSerializationFactory } doReturn cordaAvroSerializationFactory
        on { memberInfoFactory } doReturn memberInfoFactory
    }
    private val handler: SuspendMemberHandler = SuspendMemberHandler(persistenceHandlerServices)
    private val context = MembershipRequestContext(
        clock.instant(),
        UUID(0, 1).toString(),
        holdingIdentity.toAvro()
    )
    private val request = SuspendMember(knownX500Name.toString(), SERIAL_NUMBER, REASON)

    private fun invokeTestFunction(): SuspendMemberResponse {
        return handler.invoke(context, request)
    }

    @Suppress("LongParameterList")
    private fun mockMemberInfoEntity(
        mgmProvidedContext: ByteArray = mgmContextBytes,
        memberProvidedContext: ByteArray = memberContextBytes,
        group: String? = knownGroupId,
        name: MemberX500Name? = knownX500Name,
        serial: Long? = SERIAL_NUMBER,
        memberStatus: String = MEMBER_STATUS_ACTIVE
    ) {
        val mockEntity = mock<MemberInfoEntity> {
            on { mgmContext } doReturn mgmProvidedContext
            on { memberContext } doReturn memberProvidedContext
            on { groupId } doReturn group!!
            on { memberX500Name } doReturn name.toString()
            on { serialNumber } doReturn serial!!
            on { status } doReturn memberStatus
            on { memberSignatureKey } doReturn byteArrayOf(1)
            on { memberSignatureContent } doReturn byteArrayOf(2)
            on { memberSignatureSpec } doReturn ""
        }
        whenever(
            em.find(eq(MemberInfoEntity::class.java), eq(primaryKey), eq(LockModeType.PESSIMISTIC_WRITE))
        ).doReturn(mockEntity)
    }

    @Test
    fun `invoke returns the correct data`() {
        mockMemberInfoEntity()

        val persistentMemberInfo = PersistentMemberInfo(
            context.holdingIdentity,
            memberContext,
            mgmContext,
            SignedContexts(
                ByteBuffer.wrap(memberContextBytes),
                ByteBuffer.wrap(mgmContextBytes),
            ),
        )

        whenever(
            memberInfoFactory.createPersistentMemberInfo(context.holdingIdentity, memberContextBytes, suspendedMgmContextBytes)
        ).doReturn(persistentMemberInfo)

        val result = invokeTestFunction()

        assertThat(result.memberInfo).isEqualTo(persistentMemberInfo)
    }
}

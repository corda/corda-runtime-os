package net.corda.membership.impl.persistence.service.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToApproved
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.datamodel.MemberSignatureEntity
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.test.util.TestRandom
import net.corda.test.util.time.TestClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.LockModeType

class UpdateMemberAndRegistrationRequestToApprovedHandlerTest {
    private val clock = TestClock(Instant.ofEpochMilli(0))
    private val jpaEntitiesSet = mock<JpaEntitiesSet>()
    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry> {
        on { get(CordaDb.Vault.persistenceUnitName) } doReturn jpaEntitiesSet
    }
    private val memberInfoFactory = mock<MemberInfoFactory>()
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>>()
    private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(any()) } doReturn byteArrayOf(0)
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
    private val vaultDmlConnectionId = UUID(0, 0)
    private val virtualNodeInfo = VirtualNodeInfo(
        vaultDmlConnectionId = vaultDmlConnectionId,
        cpiIdentifier = CpiIdentifier(
            "", "", TestRandom.secureHash()
        ),
        cryptoDmlConnectionId = UUID(0, 0),
        uniquenessDmlConnectionId = UUID(0, 0),
        holdingIdentity = HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "").toCorda(),
        timestamp = clock.instant(),
    )
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(any()) } doReturn virtualNodeInfo
    }
    private val entityManager = mock<EntityManager> {
        on { transaction } doReturn mock()
    }
    private val factory = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn entityManager
    }
    private val dbConnectionManager = mock<DbConnectionManager> {
        on {
            createEntityManagerFactory(
                vaultDmlConnectionId,
                jpaEntitiesSet
            )
        } doReturn factory
    }
    private val requestEntity = mock<RegistrationRequestEntity> {
        on { status } doReturn RegistrationStatus.SENT_TO_MGM.name
    }
    private val keyEncodingService: KeyEncodingService = mock()
    private val platformInfoProvider: PlatformInfoProvider = mock()
    private val service = PersistenceHandlerServices(
        clock,
        dbConnectionManager,
        jpaEntitiesRegistry,
        memberInfoFactory,
        cordaAvroSerializationFactory,
        virtualNodeInfoReadService,
        keyEncodingService,
        platformInfoProvider,
        mock(),
    )
    private val handler = UpdateMemberAndRegistrationRequestToApprovedHandler(service)

    private val groupId = "group"
    private val member = HoldingIdentity("CN=Member, O=Corp, L=LDN, C=GB", groupId)
    private val mgmContextBytes = byteArrayOf(1, 10)
    private val memberContextBytes = byteArrayOf(1, 10)
    private val serial = 1L
    private val primaryKey = MemberInfoEntityPrimaryKey(
        groupId = member.groupId,
        memberX500Name = member.x500Name,
        true
    )

    private val memberInfoEntity = mock<MemberInfoEntity> {
        on { mgmContext } doReturn mgmContextBytes
        on { memberContext } doReturn memberContextBytes
        on { groupId } doReturn member.groupId
        on { memberX500Name } doReturn member.x500Name
        on { serialNumber } doReturn serial
    }

    private val signatureContentBytes = byteArrayOf(1, 5)
    private val signatureContextBytes = byteArrayOf(1, 5)
    private val publicKey = byteArrayOf(1, 2)
    private val memberSignatureEntity = mock<MemberSignatureEntity> {
        on { groupId } doReturn member.groupId
        on { memberX500Name } doReturn member.x500Name
        on { publicKey } doReturn publicKey
        on { content } doReturn signatureContentBytes
        on { context } doReturn signatureContextBytes
    }

    private val requestId = "requestId"

    private fun mockMemberInfoEntity(entity: MemberInfoEntity? = memberInfoEntity) {
        whenever(
            entityManager.find(eq(MemberInfoEntity::class.java), eq(primaryKey), eq(LockModeType.PESSIMISTIC_WRITE))
        ).doReturn(entity)
    }

    private fun mockRegistrationRequestEntity(entity: RegistrationRequestEntity? = requestEntity) {
        whenever(
            entityManager.find(eq(RegistrationRequestEntity::class.java), eq(requestId), eq(LockModeType.PESSIMISTIC_WRITE))
        ).doReturn(entity)
    }

    private fun mockMemberSignatureEntity(entity: MemberSignatureEntity? = memberSignatureEntity) {
        whenever(
            entityManager.find(eq(MemberSignatureEntity::class.java), eq(primaryKey), eq(LockModeType.PESSIMISTIC_WRITE))
        ).doReturn(entity)
    }

    @Test
    fun `invoke throws exception if member can not be found`() {
        mockMemberInfoEntity(null)
        val context = MembershipRequestContext(clock.instant(), requestId, member,)
        val request = UpdateMemberAndRegistrationRequestToApproved(member, requestId,)

        assertThrows<MembershipPersistenceException> {
            handler.invoke(context, request)
        }
    }

    @Test
    fun `invoke throws exception if request can not be found`() {
        mockMemberInfoEntity()
        mockRegistrationRequestEntity(null)
        val context = MembershipRequestContext(clock.instant(), requestId, member,)
        val request = UpdateMemberAndRegistrationRequestToApproved(member, requestId,)

        assertThrows<MembershipPersistenceException> {
            handler.invoke(context, request)
        }
    }

    @Test
    fun `invoke throws exception if signature cannot be found`() {
        mockMemberInfoEntity()
        mockRegistrationRequestEntity()
        mockMemberSignatureEntity(null)
        val context = MembershipRequestContext(clock.instant(), requestId, member,)
        val request = UpdateMemberAndRegistrationRequestToApproved(member, requestId,)

        assertThrows<MembershipPersistenceException> {
            handler.invoke(context, request)
        }
    }

    @Test
    fun `invoke will insert non-pending information for the member`() {
        whenever(keyValuePairListDeserializer.deserialize(mgmContextBytes)).doReturn(
            KeyValuePairList(listOf(KeyValuePair(STATUS, MEMBER_STATUS_PENDING)))
        )
        val entityCapture = argumentCaptor<MemberInfoEntity>()
        mockMemberInfoEntity()
        mockRegistrationRequestEntity()
        mockMemberSignatureEntity()
        whenever(entityManager.merge(entityCapture.capture())).doReturn(mock())
        val context = MembershipRequestContext(clock.instant(), requestId, member,)
        val request = UpdateMemberAndRegistrationRequestToApproved(member, requestId,)

        clock.setTime(Instant.ofEpochMilli(500))
        handler.invoke(context, request)

        with(entityCapture.firstValue) {
            assertThat(this.status).isEqualTo(MEMBER_STATUS_ACTIVE)
            assertThat(this.modifiedTime).isEqualTo(Instant.ofEpochMilli(500))
            assertThat(this.mgmContext).isEqualTo(byteArrayOf(0))
            assertThat(this.isPending).isEqualTo(false)
        }
    }

    @Test
    fun `invoke will update the MGM context members`() {
        whenever(keyValuePairListDeserializer.deserialize(mgmContextBytes)).doReturn(
            KeyValuePairList(
                listOf(
                    KeyValuePair(STATUS, MEMBER_STATUS_PENDING),
                    KeyValuePair("another-key", "value"),
                )
            )
        )
        val mgmContextCapture = argumentCaptor<KeyValuePairList>()
        whenever(keyValuePairListSerializer.serialize(mgmContextCapture.capture())).doReturn(byteArrayOf(0))
        mockMemberInfoEntity()
        mockRegistrationRequestEntity()
        mockMemberSignatureEntity()
        val context = MembershipRequestContext(clock.instant(), requestId, member,)
        val request = UpdateMemberAndRegistrationRequestToApproved(member, requestId,)

        clock.setTime(Instant.ofEpochMilli(500))
        handler.invoke(context, request)

        assertThat(mgmContextCapture.firstValue.items).containsExactly(
            KeyValuePair(STATUS, MEMBER_STATUS_ACTIVE),
            KeyValuePair("another-key", "value"),
        )
    }

    @Test
    fun `invoke will insert non-pending signature for the member`() {
        whenever(keyValuePairListDeserializer.deserialize(mgmContextBytes)).doReturn(
            KeyValuePairList(listOf(KeyValuePair(STATUS, MEMBER_STATUS_PENDING)))
        )
        val entityCapture = argumentCaptor<Any>()
        mockMemberInfoEntity()
        mockRegistrationRequestEntity()
        mockMemberSignatureEntity()
        whenever(entityManager.merge(entityCapture.capture())).doReturn(mock())
        val requestContext = MembershipRequestContext(clock.instant(), requestId, member,)
        val request = UpdateMemberAndRegistrationRequestToApproved(member, requestId,)

        clock.setTime(Instant.ofEpochMilli(500))
        handler.invoke(requestContext, request)

        with(entityCapture.secondValue as MemberSignatureEntity) {
            assertThat(this.publicKey).isEqualTo(publicKey)
            assertThat(this.isPending).isEqualTo(false)
            assertThat(this.memberX500Name).isEqualTo(member.x500Name)
            assertThat(this.groupId).isEqualTo(member.groupId)
            assertThat(this.content).isEqualTo(content)
            assertThat(this.context).isEqualTo(context)
        }
    }

    @Test
    fun `invoke will throw an exception if MGM context can not be serialize`() {
        whenever(keyValuePairListSerializer.serialize(any())).doReturn(null)
        whenever(keyValuePairListDeserializer.deserialize(mgmContextBytes)).doReturn(
            KeyValuePairList(listOf(KeyValuePair(STATUS, MEMBER_STATUS_PENDING)))
        )
        mockMemberInfoEntity()
        mockRegistrationRequestEntity()
        mockMemberSignatureEntity()
        val context = MembershipRequestContext(clock.instant(), requestId, member,)
        val request = UpdateMemberAndRegistrationRequestToApproved(member, requestId,)

        clock.setTime(Instant.ofEpochMilli(500))
        assertThrows<CordaRuntimeException> {
            handler.invoke(context, request)
        }
    }

    @Test
    fun `invoke will throw an exception if mgm context can not be deserialize`() {
        whenever(keyValuePairListDeserializer.deserialize(mgmContextBytes)).doReturn(null)
        mockMemberInfoEntity()
        mockRegistrationRequestEntity()
        mockMemberSignatureEntity()
        val context = MembershipRequestContext(clock.instant(), requestId, member,)
        val request = UpdateMemberAndRegistrationRequestToApproved(member, requestId,)

        clock.setTime(Instant.ofEpochMilli(500))
        assertThrows<MembershipPersistenceException> {
            handler.invoke(context, request)
        }
    }

    @Test
    fun `invoke updates the request`() {
        whenever(keyValuePairListDeserializer.deserialize(mgmContextBytes)).doReturn(
            KeyValuePairList(listOf(KeyValuePair(STATUS, MEMBER_STATUS_PENDING)))
        )
        mockMemberInfoEntity()
        mockMemberInfoEntity()
        mockRegistrationRequestEntity()
        mockMemberSignatureEntity()
        val context = MembershipRequestContext(clock.instant(), requestId, member,)
        val request = UpdateMemberAndRegistrationRequestToApproved(member, requestId,)

        clock.setTime(Instant.ofEpochMilli(500))
        handler.invoke(context, request)

        verify(requestEntity).status = RegistrationStatus.APPROVED.name
        verify(requestEntity).lastModified = Instant.ofEpochMilli(500)
    }

    @Test
    fun `invoke will not update a declined request`() {
        whenever(requestEntity.status).doReturn(RegistrationStatus.DECLINED.name)
        whenever(keyValuePairListDeserializer.deserialize(mgmContextBytes)).doReturn(
            KeyValuePairList(listOf(KeyValuePair(STATUS, MEMBER_STATUS_PENDING)))
        )
        mockMemberInfoEntity()
        mockRegistrationRequestEntity()
        mockMemberSignatureEntity()
        val context = MembershipRequestContext(clock.instant(), requestId, member,)
        val request = UpdateMemberAndRegistrationRequestToApproved(member, requestId,)

        assertThrows<MembershipPersistenceException> {
            handler.invoke(context, request)
        }
    }

    @Test
    fun `invoke returns the correct data`() {
        val memberInfoEntity = mock<MemberInfoEntity> {
            on { memberContext } doReturn byteArrayOf(1)
            on { mgmContext } doReturn byteArrayOf(2)
            on { groupId } doReturn member.groupId
            on { memberX500Name } doReturn member.x500Name
        }
        mockMemberInfoEntity(memberInfoEntity)
        mockRegistrationRequestEntity()
        mockMemberSignatureEntity()
        val context = MembershipRequestContext(clock.instant(), requestId, member,)
        val request = UpdateMemberAndRegistrationRequestToApproved(member, requestId,)
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

        val result = handler.invoke(context, request)

        assertThat(result.memberInfo).isEqualTo(
            PersistentMemberInfo(
                context.holdingIdentity,
                memberContext, mgmContext
            )
        )
    }
}

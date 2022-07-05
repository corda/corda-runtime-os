package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.RegistrationStatus
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToApproved
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.test.util.time.TestClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class UpdateMemberAndRegistrationRequestToApprovedHandlerTest {
    private val clock = TestClock(Instant.ofEpochMilli(0))
    private val jpaEntitiesSet = mock<JpaEntitiesSet>()
    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry> {
        on { get(CordaDb.Vault.persistenceUnitName) } doReturn jpaEntitiesSet
    }
    private val memberInfoFactory = mock<MemberInfoFactory>()
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>>()
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on {
            createAvroDeserializer(
                any(),
                eq(KeyValuePairList::class.java)
            )
        } doReturn keyValuePairListDeserializer
    }
    private val connectionId = UUID(0, 0)
    private val virtualNodeInfo = VirtualNodeInfo(
        vaultDmlConnectionId = connectionId,
        cpiIdentifier = CpiIdentifier(
            "", "", null
        ),
        cryptoDmlConnectionId = UUID(0, 0),
        holdingIdentity = HoldingIdentity("", "").toCorda(),
        timestamp = clock.instant(),
    )
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getById(any()) } doReturn virtualNodeInfo
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
                connectionId,
                jpaEntitiesSet
            )
        } doReturn factory
    }
    private val service = PersistenceHandlerServices(
        clock,
        dbConnectionManager,
        jpaEntitiesRegistry,
        memberInfoFactory,
        cordaAvroSerializationFactory,
        virtualNodeInfoReadService,
    )
    private val handler = UpdateMemberAndRegistrationRequestToApprovedHandler(service)

    @Test
    fun `invoke throws exception if member can not be fund`() {
        val member = HoldingIdentity("member", "group")
        whenever(
            entityManager.find(
                eq(MemberInfoEntity::class.java),
                eq(
                    MemberInfoEntityPrimaryKey(
                        groupId = member.groupId,
                        memberX500Name = member.x500Name,
                    )
                )
            )
        ).doReturn(null)
        val context = MembershipRequestContext(
            clock.instant(),
            "id",
            HoldingIdentity("name", "group"),
        )
        val request = UpdateMemberAndRegistrationRequestToApproved(
            HoldingIdentity("member", "group"),
            "requestId",
        )

        assertThrows<CordaRuntimeException> {
            handler.invoke(context, request)
        }
    }

    @Test
    fun `invoke throws exception if request can not be fund`() {
        val member = HoldingIdentity("member", "group")
        val entity = mock<MemberInfoEntity>()
        val requestId = "requestId"
        whenever(
            entityManager.find(
                eq(MemberInfoEntity::class.java),
                eq(
                    MemberInfoEntityPrimaryKey(
                        groupId = member.groupId,
                        memberX500Name = member.x500Name,
                    )
                )
            )
        ).doReturn(entity)
        whenever(entityManager.find(eq(RegistrationRequestEntity::class.java), eq(requestId))).doReturn(null)
        val context = MembershipRequestContext(
            clock.instant(),
            "id",
            HoldingIdentity("name", "group"),
        )
        val request = UpdateMemberAndRegistrationRequestToApproved(
            HoldingIdentity("member", "group"),
            requestId,
        )

        assertThrows<CordaRuntimeException> {
            handler.invoke(context, request)
        }
    }

    @Test
    fun `invoke updates the member`() {
        val member = HoldingIdentity("member", "group")
        val entity = mock<MemberInfoEntity>()
        val requestId = "requestId"
        whenever(
            entityManager.find(
                eq(MemberInfoEntity::class.java),
                eq(
                    MemberInfoEntityPrimaryKey(
                        groupId = member.groupId,
                        memberX500Name = member.x500Name,
                    )
                )
            )
        ).doReturn(entity)
        val requestEntity = mock<RegistrationRequestEntity>()
        whenever(entityManager.find(eq(RegistrationRequestEntity::class.java), eq(requestId))).doReturn(requestEntity)
        val context = MembershipRequestContext(
            clock.instant(),
            "id",
            HoldingIdentity("name", "group"),
        )
        val request = UpdateMemberAndRegistrationRequestToApproved(
            HoldingIdentity("member", "group"),
            requestId,
        )

        clock.setTime(Instant.ofEpochMilli(500))
        handler.invoke(context, request)

        verify(entity).status = MEMBER_STATUS_ACTIVE
        verify(entity).modifiedTime = Instant.ofEpochMilli(500)
    }

    @Test
    fun `invoke updates the request`() {
        val member = HoldingIdentity("member", "group")
        val entity = mock<MemberInfoEntity>()
        val requestId = "requestId"
        whenever(
            entityManager.find(
                eq(MemberInfoEntity::class.java),
                eq(
                    MemberInfoEntityPrimaryKey(
                        groupId = member.groupId,
                        memberX500Name = member.x500Name,
                    )
                )
            )
        ).doReturn(entity)
        val requestEntity = mock<RegistrationRequestEntity>()
        whenever(entityManager.find(eq(RegistrationRequestEntity::class.java), eq(requestId))).doReturn(requestEntity)
        val context = MembershipRequestContext(
            clock.instant(),
            "id",
            HoldingIdentity("name", "group"),
        )
        val request = UpdateMemberAndRegistrationRequestToApproved(
            HoldingIdentity("member", "group"),
            requestId,
        )

        clock.setTime(Instant.ofEpochMilli(500))
        handler.invoke(context, request)

        verify(requestEntity).status = RegistrationStatus.APPROVED.name
        verify(requestEntity).lastModified = Instant.ofEpochMilli(500)
    }

    @Test
    fun `invoke returns the correct data`() {
        val member = HoldingIdentity("member", "group")
        val entity = mock<MemberInfoEntity> {
            on { memberContext } doReturn byteArrayOf(1)
            on { mgmContext } doReturn byteArrayOf(2)
        }
        val requestId = "requestId"
        whenever(
            entityManager.find(
                eq(MemberInfoEntity::class.java),
                eq(
                    MemberInfoEntityPrimaryKey(
                        groupId = member.groupId,
                        memberX500Name = member.x500Name,
                    )
                )
            )
        ).doReturn(entity)
        val requestEntity = mock<RegistrationRequestEntity>()
        whenever(entityManager.find(eq(RegistrationRequestEntity::class.java), eq(requestId))).doReturn(requestEntity)
        val context = MembershipRequestContext(
            clock.instant(),
            "id",
            HoldingIdentity("name", "group"),
        )
        val request = UpdateMemberAndRegistrationRequestToApproved(
            HoldingIdentity("member", "group"),
            requestId,
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

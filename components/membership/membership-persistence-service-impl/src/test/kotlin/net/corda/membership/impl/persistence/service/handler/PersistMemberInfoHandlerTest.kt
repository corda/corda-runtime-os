package net.corda.membership.impl.persistence.service.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.ShortHash
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.test.util.TestRandom
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

class PersistMemberInfoHandlerTest {

    private val ourX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val ourGroupId = UUID.randomUUID().toString()
    private val ourHoldingIdentity = HoldingIdentity(
        ourX500Name,
        ourGroupId
    )
    private val ourRegistrationId = UUID.randomUUID().toString()
    private val clock = TestClock(Instant.ofEpochSecond(0))

    private val memberProvidedContext: MemberContext = mock()
    private val mgmProvidedContext: MGMContext = mock()
    private val ourMemberInfo: MemberInfo = mock {
        on { memberProvidedContext } doReturn memberProvidedContext
        on { mgmProvidedContext } doReturn mgmProvidedContext
        on { groupId } doReturn ourGroupId
        on { name } doReturn ourX500Name
        on { status } doReturn MEMBER_STATUS_ACTIVE
        on { serial } doReturn 1
    }
    private val vaultDmlConnectionId = UUID(0, 1)

    private val virtualNodeInfo = VirtualNodeInfo(
        ourHoldingIdentity,
        CpiIdentifier("TEST_CPI", "1.0", TestRandom.secureHash()),
        vaultDmlConnectionId = vaultDmlConnectionId,
        cryptoDmlConnectionId = UUID(0, 0),
        uniquenessDmlConnectionId = UUID(0, 0),
        timestamp = clock.instant()
    )

    private val entityTransaction: EntityTransaction = mock()
    private val entityManager: EntityManager = mock {
        on { transaction } doReturn entityTransaction
    }
    private val entityManagerFactory: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn entityManager
    }

    private val dbConnectionManager: DbConnectionManager = mock {
        on { createEntityManagerFactory(
            eq(vaultDmlConnectionId),
            any()) } doReturn entityManagerFactory
    }
    private val jpaEntitiesRegistry: JpaEntitiesRegistry = mock {
        on { get(eq(CordaDb.Vault.persistenceUnitName)) } doReturn mock()
    }
    private val memberInfoFactory: MemberInfoFactory = mock {
        on { create(any()) } doReturn ourMemberInfo
    }
    private val keyValueSerializer: CordaAvroSerializer<KeyValuePairList> = mock {
        on { serialize(any()) } doReturn "123".toByteArray()
    }
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn keyValueSerializer
    }
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getByHoldingIdentityShortHash(eq(ourHoldingIdentity.shortHash)) } doReturn virtualNodeInfo
    }
    private val keyEncodingService: KeyEncodingService = mock()
    private val platformInfoProvider: PlatformInfoProvider = mock()

    private val services = PersistenceHandlerServices(
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
    private lateinit var persistMemberInfoHandler: PersistMemberInfoHandler

    @BeforeEach
    fun setUp() {
        persistMemberInfoHandler = PersistMemberInfoHandler(services)
    }

    private fun getMemberRequestContext() = MembershipRequestContext(
        clock.instant(),
        ourRegistrationId,
        ourHoldingIdentity.toAvro(),
    )

    private fun getPersistMemberInfo(memberInfos: List<PersistentMemberInfo>) = PersistMemberInfo(
        memberInfos
    )

    private fun getPersistentMemberInfo() = PersistentMemberInfo(
        ourHoldingIdentity.toAvro(),
        KeyValuePairList(emptyList()),
        KeyValuePairList(emptyList())
    )


    @Test
    fun `invoke with no members does nothing`() {
        val result = persistMemberInfoHandler.invoke(
            getMemberRequestContext(),
            getPersistMemberInfo(emptyList())
        )

        assertThat(result).isInstanceOf(Unit::class.java)
        verify(memberInfoFactory, never()).create(any())
        verify(virtualNodeInfoReadService, never()).getByHoldingIdentityShortHash(any())
        verify(dbConnectionManager, never()).getOrCreateEntityManagerFactory(any(), any(), any())
        verify(jpaEntitiesRegistry, never()).get(any())
    }

    @Test
    fun `invoke with members persists`() {
        val memberInfo = getPersistentMemberInfo()
        val result = persistMemberInfoHandler.invoke(
            getMemberRequestContext(),
            getPersistMemberInfo(listOf(memberInfo))
        )

        assertThat(result).isInstanceOf(Unit::class.java)
        with(argumentCaptor<ShortHash>()) {
            verify(virtualNodeInfoReadService).getByHoldingIdentityShortHash(capture())
            assertThat(firstValue).isEqualTo(ourHoldingIdentity.shortHash)
        }
        verify(entityManagerFactory).createEntityManager()
        verify(entityManagerFactory).close()
        verify(entityManager).transaction
        verify(jpaEntitiesRegistry).get(eq(CordaDb.Vault.persistenceUnitName))
        with(argumentCaptor<PersistentMemberInfo>()) {
            verify(memberInfoFactory).create(capture())
            assertThat(firstValue).isEqualTo(memberInfo)
        }
        with(argumentCaptor<Any>()) {
            verify(entityManager).merge(capture())
            assertThat(firstValue).isInstanceOf(MemberInfoEntity::class.java)
            val entity = firstValue as MemberInfoEntity
            assertThat(entity.groupId).isEqualTo(ourGroupId)
            assertThat(entity.memberX500Name).isEqualTo(ourX500Name.toString())
        }
    }

}

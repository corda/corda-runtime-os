package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.ShortHash
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedData
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.lib.MemberInfoFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.test.util.TestRandom
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
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
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType

class PersistRegistrationRequestHandlerTest {

    private val ourX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val ourGroupId = "cbdc24f5-35b0-4ef3-be9e-f428d273d7b1"
    private val ourHoldingIdentity = HoldingIdentity(
        ourX500Name,
        ourGroupId
    )
    private val ourRegistrationId = UUID.randomUUID().toString()
    private val clock = TestClock(Instant.ofEpochSecond(0))
    private val vaultDmlConnectionId = UUID(12, 0)
    private val memberContext = "89".toByteArray()
    private val memberContextSignatureKey = "123".toByteArray()
    private val memberContextSignatureContent = "456".toByteArray()
    private val memberContextSignatureSpec = "dummySignature"
    private val registrationContext = "registrationContext".toByteArray()
    private val registrationContextSignatureKey = "registrationContextSignatureKey".toByteArray()
    private val registrationContextSignatureContent = "registrationContextSignatureContent".toByteArray()
    private val registrationContextSignatureSpec = "registrationContextSignatureSpec"

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
        on {
            getOrCreateEntityManagerFactory(
                eq(vaultDmlConnectionId),
                any(),
                eq(false)
            )
        } doReturn entityManagerFactory
    }
    private val jpaEntitiesRegistry: JpaEntitiesRegistry = mock {
        on { get(eq(CordaDb.Vault.persistenceUnitName)) } doReturn mock()
    }
    private val memberInfoFactory: MemberInfoFactory = mock()
    private val serializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(any()) } doReturn byteArrayOf(1, 3, 4)
    }
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn serializer
    }
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getByHoldingIdentityShortHash(eq(ourHoldingIdentity.shortHash)) } doReturn virtualNodeInfo
    }
    private val keyEncodingService: KeyEncodingService = mock()
    private val platformInfoProvider: PlatformInfoProvider = mock()
    private val transactionTimerFactory = { _: String -> transactionTimer }
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
        mock(),
        mock(),
        transactionTimerFactory,
    )
    private lateinit var persistRegistrationRequestHandler: PersistRegistrationRequestHandler

    @BeforeEach
    fun setUp() {
        persistRegistrationRequestHandler = PersistRegistrationRequestHandler(services)
    }

    private fun getMemberRequestContext() = MembershipRequestContext(
        clock.instant(),
        ourRegistrationId,
        ourHoldingIdentity.toAvro(),
    )

    private fun getPersistRegistrationRequest(
        status: RegistrationStatus = RegistrationStatus.PENDING_MEMBER_VERIFICATION
    ): PersistRegistrationRequest {
        val memberContext = SignedData(
            ByteBuffer.wrap(memberContext),
            CryptoSignatureWithKey(
                ByteBuffer.wrap(memberContextSignatureKey),
                ByteBuffer.wrap(memberContextSignatureContent)
            ),
            CryptoSignatureSpec(memberContextSignatureSpec, null, null)
        )
        val registrationContext = SignedData(
            ByteBuffer.wrap(registrationContext),
            CryptoSignatureWithKey(
                ByteBuffer.wrap(registrationContextSignatureKey),
                ByteBuffer.wrap(registrationContextSignatureContent)
            ),
            CryptoSignatureSpec(registrationContextSignatureSpec, null, null)
        )
        return PersistRegistrationRequest(
            status,
            ourHoldingIdentity.toAvro(),
            MembershipRegistrationRequest(
                ourRegistrationId,
                memberContext,
                registrationContext,
                0L,
            )
        )
    }

    @Test
    fun `invoke with registration request`() {
        val mergedEntity = argumentCaptor<Any>()
        whenever(entityManager.merge(mergedEntity.capture())).doReturn(null)

        val result = persistRegistrationRequestHandler.invoke(
            getMemberRequestContext(),
            getPersistRegistrationRequest()
        )

        assertThat(result).isInstanceOf(Unit::class.java)
        with(argumentCaptor<ShortHash>()) {
            verify(virtualNodeInfoReadService).getByHoldingIdentityShortHash(capture())
            assertThat(firstValue).isEqualTo(ourHoldingIdentity.shortHash)
        }
        verify(entityManagerFactory).createEntityManager()
        verify(entityManager).transaction
        verify(jpaEntitiesRegistry).get(eq(CordaDb.Vault.persistenceUnitName))
        verify(memberInfoFactory, never()).createMemberInfo(any())
        with(mergedEntity.firstValue) {
            assertThat(this).isInstanceOf(RegistrationRequestEntity::class.java)
            val entity = this as RegistrationRequestEntity
            assertThat(entity.registrationId).isEqualTo(ourRegistrationId)
            assertThat(entity.holdingIdentityShortHash).isEqualTo(ourHoldingIdentity.shortHash.value)
            assertThat(entity.status).isEqualTo(RegistrationStatus.PENDING_MEMBER_VERIFICATION.toString())
            assertThat(entity.created).isBeforeOrEqualTo(clock.instant())
            assertThat(entity.lastModified).isBeforeOrEqualTo(clock.instant())
            assertThat(entity.memberContext)
                .isEqualTo(this@PersistRegistrationRequestHandlerTest.memberContext)
            assertThat(entity.memberContextSignatureKey)
                .isEqualTo(this@PersistRegistrationRequestHandlerTest.memberContextSignatureKey)
            assertThat(entity.memberContextSignatureContent)
                .isEqualTo(this@PersistRegistrationRequestHandlerTest.memberContextSignatureContent)
            assertThat(entity.memberContextSignatureSpec)
                .isEqualTo(this@PersistRegistrationRequestHandlerTest.memberContextSignatureSpec)
            assertThat(entity.registrationContext)
                .isEqualTo(this@PersistRegistrationRequestHandlerTest.registrationContext)
            assertThat(entity.registrationContextSignatureKey)
                .isEqualTo(this@PersistRegistrationRequestHandlerTest.registrationContextSignatureKey)
            assertThat(entity.registrationContextSignatureContent)
                .isEqualTo(this@PersistRegistrationRequestHandlerTest.registrationContextSignatureContent)
            assertThat(entity.registrationContextSignatureSpec)
                .isEqualTo(this@PersistRegistrationRequestHandlerTest.registrationContextSignatureSpec)
        }
    }

    @Test
    fun `invoke will not merge anything if the status as already moved on`() {
        val status = mock<RegistrationRequestEntity> {
            on { status } doReturn RegistrationStatus.APPROVED.toString()
        }
        whenever(
            entityManager.find(
                RegistrationRequestEntity::class.java,
                ourRegistrationId,
                LockModeType.PESSIMISTIC_WRITE,
            )
        ).doReturn(status)

        persistRegistrationRequestHandler.invoke(
            getMemberRequestContext(),
            getPersistRegistrationRequest()
        )

        verify(entityManager, never()).merge(any<RegistrationRequestEntity>())
    }

    @Test
    fun `invoke will merge nothing except serial if the status is sent to MGM and serial is null`() {
        val now = clock.instant().minusSeconds(100)
        val previousEntity = mock<RegistrationRequestEntity> {
            on { status } doReturn RegistrationStatus.APPROVED.toString()
            on { serial } doReturn null
            on { registrationId } doReturn ourRegistrationId
            on { holdingIdentityShortHash } doReturn ourHoldingIdentity.shortHash.value
            on { created } doReturn now
            on { lastModified } doReturn now
            on { memberContext } doReturn memberContext
            on { memberContextSignatureKey } doReturn memberContextSignatureKey
            on { memberContextSignatureContent } doReturn memberContextSignatureContent
            on { memberContextSignatureSpec } doReturn memberContextSignatureSpec
            on { registrationContext } doReturn registrationContext
            on { registrationContextSignatureKey } doReturn registrationContextSignatureKey
            on { registrationContextSignatureContent } doReturn registrationContextSignatureContent
            on { registrationContextSignatureSpec } doReturn registrationContextSignatureSpec
        }
        whenever(
            entityManager.find(
                RegistrationRequestEntity::class.java,
                ourRegistrationId,
                LockModeType.PESSIMISTIC_WRITE,
            )
        ).doReturn(previousEntity)

        persistRegistrationRequestHandler.invoke(
            getMemberRequestContext(),
            getPersistRegistrationRequest(RegistrationStatus.SENT_TO_MGM),
        )
        val mergedEntity = argumentCaptor<Any>()
        verify(entityManager).merge(mergedEntity.capture())

        with(mergedEntity.firstValue) {
            assertThat(this).isInstanceOf(RegistrationRequestEntity::class.java)
            val entity = this as RegistrationRequestEntity
            assertThat(entity.status).isEqualTo(RegistrationStatus.APPROVED.toString())
            assertThat(entity.serial).isEqualTo(0L)
            assertThat(entity.registrationId).isEqualTo(previousEntity.registrationId)
            assertThat(entity.holdingIdentityShortHash).isEqualTo(previousEntity.holdingIdentityShortHash)
            assertThat(entity.created).isEqualTo(now)
            assertThat(entity.lastModified).isAfter(now)
            assertThat(entity.memberContext).isEqualTo(previousEntity.memberContext)
            assertThat(entity.memberContextSignatureKey).isEqualTo(previousEntity.memberContextSignatureKey)
            assertThat(entity.memberContextSignatureContent).isEqualTo(previousEntity.memberContextSignatureContent)
            assertThat(entity.memberContextSignatureSpec).isEqualTo(previousEntity.memberContextSignatureSpec)
            assertThat(entity.registrationContext).isEqualTo(previousEntity.registrationContext)
            assertThat(entity.registrationContextSignatureKey).isEqualTo(previousEntity.registrationContextSignatureKey)
            assertThat(entity.registrationContextSignatureContent).isEqualTo(entity.registrationContextSignatureContent)
            assertThat(entity.registrationContextSignatureSpec).isEqualTo(entity.registrationContextSignatureSpec)
        }
    }

    @Test
    fun `invoke will not merge anything if the status is sent to MGM and serial is not null`() {
        val status = mock<RegistrationRequestEntity> {
            on { status } doReturn RegistrationStatus.APPROVED.toString()
            on { serial } doReturn 1L
        }
        whenever(
            entityManager.find(
                RegistrationRequestEntity::class.java,
                ourRegistrationId,
                LockModeType.PESSIMISTIC_WRITE,
            )
        ).doReturn(status)

        persistRegistrationRequestHandler.invoke(
            getMemberRequestContext(),
            getPersistRegistrationRequest(RegistrationStatus.SENT_TO_MGM),
        )

        verify(entityManager, never()).merge(any<RegistrationRequestEntity>())
    }

    @Test
    fun `invoke will merge if the status is in earlier state`() {
        val status = mock<RegistrationRequestEntity> {
            on { status } doReturn RegistrationStatus.NEW.toString()
        }
        whenever(
            entityManager.find(
                RegistrationRequestEntity::class.java,
                ourRegistrationId,
                LockModeType.PESSIMISTIC_WRITE,
            )
        ).doReturn(status)

        persistRegistrationRequestHandler.invoke(
            getMemberRequestContext(),
            getPersistRegistrationRequest()
        )

        verify(entityManager).merge(any<RegistrationRequestEntity>())
    }
}

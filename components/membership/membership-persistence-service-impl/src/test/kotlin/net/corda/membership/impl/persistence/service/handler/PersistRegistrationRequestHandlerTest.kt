package net.corda.membership.impl.persistence.service.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedData
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.lib.MemberInfoFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
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
    private val memberContext = "89".toByteArray()
    private val memberContextSignatureKey = "123".toByteArray()
    private val memberContextSignatureContent = "456".toByteArray()
    private val memberContextSignatureSpec = "dummySignature"
    private val registrationContext = "registrationContext".toByteArray()
    private val registrationContextSignatureKey = "registrationContextSignatureKey".toByteArray()
    private val registrationContextSignatureContent = "registrationContextSignatureContent".toByteArray()
    private val registrationContextSignatureSpec = "registrationContextSignatureSpec"

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
                any(),
                any(),
                any(),
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
    private val keyEncodingService: KeyEncodingService = mock()
    private val platformInfoProvider: PlatformInfoProvider = mock()

    private val services = PersistenceHandlerServices(
        clock,
        dbConnectionManager,
        jpaEntitiesRegistry,
        memberInfoFactory,
        cordaAvroSerializationFactory,
        keyEncodingService,
        platformInfoProvider,
        mock(),
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

    private fun getPersistRegistrationRequest(): PersistRegistrationRequest {
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
            RegistrationStatus.SENT_TO_MGM,
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
        verify(entityManagerFactory).createEntityManager()
        verify(entityManager).transaction
        verify(jpaEntitiesRegistry).get(eq(CordaDb.Vault.persistenceUnitName))
        verify(memberInfoFactory, never()).create(any())
        with(mergedEntity.firstValue) {
            assertThat(this).isInstanceOf(RegistrationRequestEntity::class.java)
            val entity = this as RegistrationRequestEntity
            assertThat(entity.registrationId).isEqualTo(ourRegistrationId)
            assertThat(entity.holdingIdentityShortHash).isEqualTo(ourHoldingIdentity.shortHash.value)
            assertThat(entity.status).isEqualTo(RegistrationStatus.SENT_TO_MGM.toString())
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
            on { status } doReturn "APPROVED"
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
    fun `invoke will merge if the status is in earlier state`() {
        val status = mock<RegistrationRequestEntity> {
            on { status } doReturn "NEW"
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

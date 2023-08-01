package net.corda.membership.impl.persistence.service.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.UpdateRegistrationRequestStatus
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.impl.persistence.service.RecoverableException
import net.corda.membership.lib.MemberInfoFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.test.util.TestRandom
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType

class UpdateRegistrationRequestStatusHandlerTest {
    private companion object {
        const val REASON = "test reason"
    }

    private val clock = TestClock(Instant.ofEpochSecond(0))
    private val ourX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val ourGroupId = "cbdc24f5-35b0-4ef3-be9e-f428d273d7b1"
    private val ourHoldingIdentity = HoldingIdentity(
        ourX500Name,
        ourGroupId
    )

    private val vaultDmlConnectionId = UUID(0, 11)
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
        on { merge(any<Any>()) } doAnswer { it.arguments[0] }
    }
    private val entityManagerFactory: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn entityManager
    }

    private val dbConnectionManager: DbConnectionManager = mock {
        on {
            getOrCreateEntityManagerFactory(
                eq(vaultDmlConnectionId),
                any()
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
        transactionTimerFactory
    )
    private lateinit var updateRegistrationRequestStatusHandler: UpdateRegistrationRequestStatusHandler

    @BeforeEach
    fun setUp() {
        updateRegistrationRequestStatusHandler = UpdateRegistrationRequestStatusHandler(services)
    }

    @Test
    fun `invoke throws exception when registration request cannot be found`() {
        val registrationId = "regId"
        whenever(
            entityManager.find(
                eq(RegistrationRequestEntity::class.java),
                eq(registrationId),
                eq(LockModeType.PESSIMISTIC_WRITE),
            )
        ).doReturn(null)
        val context = MembershipRequestContext(clock.instant(), "ID", ourHoldingIdentity.toAvro())
        val statusUpdate = UpdateRegistrationRequestStatus(registrationId, RegistrationStatus.PENDING_AUTO_APPROVAL, REASON)
        assertThrows<RecoverableException> {
            updateRegistrationRequestStatusHandler.invoke(context, statusUpdate)
        }
    }

    @Test
    fun `invoke updates registration request status`() {
        val registrationId = "regId"
        val registrationRequestEntity = mock<RegistrationRequestEntity> {
            on { status } doReturn "SENT_TO_MGM"
        }
        whenever(
            entityManager.find(
                eq(RegistrationRequestEntity::class.java),
                eq(registrationId),
                eq(LockModeType.PESSIMISTIC_WRITE),
            )
        ).doReturn(registrationRequestEntity)
        val context = MembershipRequestContext(clock.instant(), "ID", ourHoldingIdentity.toAvro())
        val statusUpdate = UpdateRegistrationRequestStatus(registrationId, RegistrationStatus.PENDING_AUTO_APPROVAL, REASON)
        clock.setTime(Instant.ofEpochMilli(500))
        updateRegistrationRequestStatusHandler.invoke(context, statusUpdate)

        verify(registrationRequestEntity).status = RegistrationStatus.PENDING_AUTO_APPROVAL.name
        verify(registrationRequestEntity).lastModified = Instant.ofEpochMilli(500)
        verify(registrationRequestEntity).reason = REASON
        verify(entityManager, times(1)).merge(registrationRequestEntity)
    }

    @Test
    fun `invoke updates ignores to downgrade status`() {
        val registrationId = "regId"
        val registrationRequestEntity = mock<RegistrationRequestEntity> {
            on { status } doReturn "APPROVED"
        }
        whenever(
            entityManager.find(
                eq(RegistrationRequestEntity::class.java),
                eq(registrationId),
                eq(LockModeType.PESSIMISTIC_WRITE),
            )
        ).doReturn(registrationRequestEntity)
        val context = MembershipRequestContext(clock.instant(), "ID", ourHoldingIdentity.toAvro())
        val statusUpdate = UpdateRegistrationRequestStatus(registrationId, RegistrationStatus.PENDING_AUTO_APPROVAL, REASON)
        clock.setTime(Instant.ofEpochMilli(500))

        updateRegistrationRequestStatusHandler.invoke(context, statusUpdate)

        verify(entityManager, never()).merge(registrationRequestEntity)
    }
}

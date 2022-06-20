package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.command.RegistrationStatus
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.lib.MemberInfoFactory
import net.corda.orm.JpaEntitiesRegistry
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
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

class PersistRegistrationRequestHandlerTest {

    private val ourX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val ourGroupId = "cbdc24f5-35b0-4ef3-be9e-f428d273d7b1"
    private val ourHoldingIdentity = HoldingIdentity(
        ourX500Name.toString(),
        ourGroupId
    )
    private val ourRegistrationId = UUID.randomUUID().toString()
    private val clock = TestClock(Instant.now())

    private val vaultDmlConnectionId = UUID.randomUUID()
    private val cryptoDmlConnectionId = UUID.randomUUID()
    private val virtualNodeInfo = VirtualNodeInfo(
        ourHoldingIdentity,
        CpiIdentifier("TEST_CPI", "1.0", null),
        vaultDmlConnectionId = vaultDmlConnectionId,
        cryptoDmlConnectionId = cryptoDmlConnectionId,
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
        on { createEntityManagerFactory(eq(vaultDmlConnectionId), any()) } doReturn entityManagerFactory
    }
    private val jpaEntitiesRegistry: JpaEntitiesRegistry = mock {
        on { get(eq(CordaDb.Vault.persistenceUnitName)) } doReturn mock()
    }
    private val memberInfoFactory: MemberInfoFactory = mock()
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock()
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getById(eq(ourHoldingIdentity.id)) } doReturn virtualNodeInfo
    }

    private val services = PersistenceHandlerServices(
        clock,
        dbConnectionManager,
        jpaEntitiesRegistry,
        memberInfoFactory,
        cordaAvroSerializationFactory,
        virtualNodeInfoReadService
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

    private fun getPersistRegistrationRequest() = PersistRegistrationRequest(
        RegistrationStatus.NEW,
        ourHoldingIdentity.toAvro(),
        MembershipRegistrationRequest(
            ourRegistrationId,
            ByteBuffer.wrap("membercontext".toByteArray()),
            CryptoSignatureWithKey(
                ByteBuffer.wrap("123".toByteArray()),
                ByteBuffer.wrap("456".toByteArray()),
                KeyValuePairList(emptyList())
            )
        )
    )


    @Test
    fun `invoke with registration request`() {
        val result = persistRegistrationRequestHandler.invoke(
            getMemberRequestContext(),
            getPersistRegistrationRequest()
        )

        assertThat(result).isNull()
        with(argumentCaptor<String>()) {
            verify(virtualNodeInfoReadService).getById(capture())
            assertThat(firstValue).isEqualTo(ourHoldingIdentity.id)
        }
        verify(dbConnectionManager).createEntityManagerFactory(any(), any())
        verify(entityManagerFactory).createEntityManager()
        verify(entityManager).transaction
        verify(jpaEntitiesRegistry).get(eq(CordaDb.Vault.persistenceUnitName))
        verify(memberInfoFactory, never()).create(any())
        with(argumentCaptor<Any>()) {
            verify(entityManager).merge(capture())
            assertThat(firstValue).isInstanceOf(RegistrationRequestEntity::class.java)
            val entity = firstValue as RegistrationRequestEntity
            assertThat(entity.registrationId).isEqualTo(ourRegistrationId)
            assertThat(entity.holdingIdentityId).isEqualTo(ourHoldingIdentity.id)
            assertThat(entity.status).isEqualTo(RegistrationStatus.NEW.toString())
            assertThat(entity.created).isBeforeOrEqualTo(clock.instant())
            assertThat(entity.lastModified).isBeforeOrEqualTo(clock.instant())
        }
    }

}
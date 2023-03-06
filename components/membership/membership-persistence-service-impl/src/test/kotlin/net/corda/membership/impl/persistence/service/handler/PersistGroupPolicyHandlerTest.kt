package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistGroupPolicy
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.GroupPolicyEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.test.util.time.TestClock
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType

class PersistGroupPolicyHandlerTest {
    private val context = byteArrayOf(1, 2, 3)
    private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(any()) } doReturn context
    }
    private val mockKeyPairList = mock<KeyValuePairList>()
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(any()) } doReturn mockKeyPairList
    }
    private val serializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn keyValuePairListSerializer
        on { createAvroDeserializer<KeyValuePairList>(any(), any())} doReturn keyValuePairListDeserializer
    }
    private val identity = HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group").toCorda()
    private val vaultDmlConnectionId = UUID(1, 2)
    private val nodeInfo = mock<VirtualNodeInfo> {
        on { holdingIdentity } doReturn identity
        on { vaultDmlConnectionId } doReturn vaultDmlConnectionId
    }
    private val nodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(any()) } doReturn nodeInfo
    }
    private val entitySet = mock<JpaEntitiesSet>()
    private val registry = mock<JpaEntitiesRegistry> {
        on { get(CordaDb.Vault.persistenceUnitName) } doReturn entitySet
    }
    private val transaction = mock<EntityTransaction>()
    private val persistCapture = argumentCaptor<GroupPolicyEntity>()
    private val entityManager = mock<EntityManager> {
        on { persist(persistCapture.capture()) } doAnswer {}
        on { transaction } doReturn transaction
    }
    private val entityManagerFactory = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn entityManager
    }
    private val connectionManager = mock<DbConnectionManager> {
        on {
            createEntityManagerFactory(
                vaultDmlConnectionId,
                entitySet
            )
        } doReturn entityManagerFactory
    }
    private val clock = TestClock(Instant.ofEpochMilli(10))
    private val persistenceHandlerServices = mock<PersistenceHandlerServices> {
        on { cordaAvroSerializationFactory } doReturn serializationFactory
        on { virtualNodeInfoReadService } doReturn nodeInfoReadService
        on { jpaEntitiesRegistry } doReturn registry
        on { dbConnectionManager } doReturn connectionManager
        on { clock } doReturn clock
    }
    private val handler = PersistGroupPolicyHandler(persistenceHandlerServices)

    @Test
    fun `invoke persists a group policy with version 1 when nothing already persisted`() {
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group")
        }
        val request = mock<PersistGroupPolicy> {
            on { properties } doReturn KeyValuePairList(
                listOf(
                    KeyValuePair("1", "one"),
                    KeyValuePair("2", "two"),
                )
            )
            on { version } doReturn 1L
        }

        handler.invoke(requestContext, request)

        persistCapture.lastValue.apply {
            assertThat(this.version).isEqualTo(1L)
            assertThat(this.properties).isEqualTo(context)
        }
    }

    @Test
    fun `invoke persists a group policy with version 2 when version 1 already persisted`() {
        whenever(entityManager.find(eq(GroupPolicyEntity::class.java), eq(1L), any<LockModeType>())).thenReturn(mock())
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group")
        }
        val request = mock<PersistGroupPolicy> {
            on { properties } doReturn KeyValuePairList(
                listOf(
                    KeyValuePair("1", "one"),
                    KeyValuePair("2", "two"),
                )
            )
            on { version } doReturn 2L
        }

        handler.invoke(requestContext, request)

        persistCapture.lastValue.apply {
            assertThat(this.version).isEqualTo(2L)
            assertThat(this.properties).isEqualTo(context)
        }
    }

    @Test
    fun `invoke does not persists a group policy with version 1 when version 1 already persisted`() {
        val mockEntity = mock<GroupPolicyEntity> {
            on { properties } doReturn context
        }
        whenever(entityManager.find(eq(GroupPolicyEntity::class.java), eq(1L), any<LockModeType>())).doReturn(mockEntity)
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group")
        }
        val request = mock<PersistGroupPolicy> {
            on { properties } doReturn mockKeyPairList
            on { version } doReturn 1L
        }

        handler.invoke(requestContext, request)

        verify(entityManager, never()).persist(any())
    }

    @Test
    fun `invoke throws when persisting a different group policy with version 1 when version 1 already persisted`() {
        val mockEntity = mock<GroupPolicyEntity> {
            on { properties } doReturn context
        }
        whenever(entityManager.find(eq(GroupPolicyEntity::class.java), eq(1L), any<LockModeType>())).thenReturn(mockEntity)
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group")
        }
        val request = mock<PersistGroupPolicy> {
            on { properties } doReturn KeyValuePairList(
                listOf(
                    KeyValuePair("1", "one"),
                    KeyValuePair("2", "two"),
                )
            )
            on { version } doReturn 1L
        }

        assertThrows<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        verify(entityManager, never()).persist(any())
    }

    @Test
    fun `invoke throws when trying to persist a version smaller than 1`() {
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group")
        }
        val request = mock<PersistGroupPolicy> {
            on { version } doReturn 0L
        }

        assertThrows<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        verify(entityManager, never()).persist(any())
    }

    @Test
    fun `invoke throws when trying to persist version 2 when nothing already persisted`() {
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group")
        }
        val request = mock<PersistGroupPolicy> {
            on { properties } doReturn KeyValuePairList(
                listOf(
                    KeyValuePair("1", "one"),
                    KeyValuePair("2", "two"),
                )
            )
            on { version } doReturn 2L
        }

        assertThrows<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        verify(entityManager, never()).persist(any())
    }

}

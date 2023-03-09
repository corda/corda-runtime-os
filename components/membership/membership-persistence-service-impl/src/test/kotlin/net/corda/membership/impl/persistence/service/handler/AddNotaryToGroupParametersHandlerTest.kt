package net.corda.membership.impl.persistence.service.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.AddNotaryToGroupParameters
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.lib.notary.MemberNotaryKey
import net.corda.membership.lib.toWire
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType
import javax.persistence.TypedQuery
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Order
import javax.persistence.criteria.Path
import javax.persistence.criteria.Root
import kotlin.test.assertFailsWith

class AddNotaryToGroupParametersHandlerTest {
    private companion object {
        const val EPOCH = 1
        const val KNOWN_NOTARY_SERVICE = "O=NotaryA, L=LDN, C=GB"
        const val KNOWN_NOTARY_PLUGIN = "net.corda.notary.MyNotaryService"
    }

    private val knownIdentity = HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group")
    private val context = byteArrayOf(1, 2, 3)
    private val serializeCaptor = argumentCaptor<KeyValuePairList>()
    private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(serializeCaptor.capture()) } doReturn context
    }
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>>()
    private val serializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn keyValuePairListSerializer
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn keyValuePairListDeserializer
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
    private val resultList: List<GroupParametersEntity> = mock {
        on { isEmpty() } doReturn false
    }
    private val previousEntry: TypedQuery<GroupParametersEntity> = mock {
        on { resultList } doReturn resultList
        on { singleResult } doReturn GroupParametersEntity(
            epoch = EPOCH,
            parameters = "test".toByteArray(),
            signaturePublicKey = byteArrayOf(0),
            signatureContent = byteArrayOf(1),
            signatureContext = byteArrayOf(2)
        )
    }
    private val groupParametersQuery: TypedQuery<GroupParametersEntity> = mock {
        on { setLockMode(LockModeType.PESSIMISTIC_WRITE) } doReturn mock
        on { setMaxResults(1) } doReturn previousEntry
    }
    private val root = mock<Root<GroupParametersEntity>> {
        on { get<String>("epoch") } doReturn mock<Path<String>>()
    }
    private val order = mock<Order>()
    private val query = mock<CriteriaQuery<GroupParametersEntity>> {
        on { from(GroupParametersEntity::class.java) } doReturn root
        on { select(root) } doReturn mock
        on { orderBy(order) } doReturn mock
    }
    private val criteriaBuilder = mock<CriteriaBuilder> {
        on { createQuery(GroupParametersEntity::class.java) } doReturn query
        on { desc(any()) } doReturn order
    }
    private val entityManager = mock<EntityManager> {
        on { persist(any<GroupParametersEntity>()) } doAnswer {}
        on { criteriaBuilder } doReturn criteriaBuilder
        on { createQuery(eq(query)) } doReturn groupParametersQuery
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
    private val keyEncodingService = mock<KeyEncodingService> {
        on { encodeAsString(any()) } doReturn "test-key"
        on { encodeAsByteArray(any()) } doReturn "test-key".toByteArray()
    }
    private val persistenceHandlerServices = mock<PersistenceHandlerServices> {
        on { cordaAvroSerializationFactory } doReturn serializationFactory
        on { virtualNodeInfoReadService } doReturn nodeInfoReadService
        on { jpaEntitiesRegistry } doReturn registry
        on { dbConnectionManager } doReturn connectionManager
        on { clock } doReturn clock
        on { keyEncodingService } doReturn keyEncodingService
    }
    private val handler = AddNotaryToGroupParametersHandler(persistenceHandlerServices)

    @Test
    fun `invoke with new notary service name adds new notary service`() {
        val knownKey = mock<MemberNotaryKey> {
            on { publicKey } doReturn mock()
        }
        val notaryDetails = mock<MemberNotaryDetails> {
            on { keys } doReturn listOf(knownKey)
            on { serviceName } doReturn MemberX500Name.parse(KNOWN_NOTARY_SERVICE)
            on { servicePlugin } doReturn KNOWN_NOTARY_PLUGIN
        }
        val memberContext: MemberContext = mock {
            on { entries } doReturn mapOf("$ROLES_PREFIX.0" to NOTARY_ROLE).entries
            on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn notaryDetails
        }
        val mgmContext: MGMContext = mock {
            on { entries } doReturn mapOf("a" to "b").entries
        }
        val notaryInRequest: MemberInfo = mock {
            on { memberProvidedContext } doReturn memberContext
            on { mgmProvidedContext } doReturn mgmContext
        }
        val memberInfoFactory = mock<MemberInfoFactory> {
            on { create(any()) } doReturn notaryInRequest
        }
        whenever(persistenceHandlerServices.memberInfoFactory).doReturn(memberInfoFactory)
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn knownIdentity
        }
        val persistentNotary = PersistentMemberInfo(
            knownIdentity,
            notaryInRequest.memberProvidedContext.toWire(),
            notaryInRequest.mgmProvidedContext.toWire()
        )
        val request = mock<AddNotaryToGroupParameters> {
            on { notary } doReturn persistentNotary
        }
        whenever(keyValuePairListDeserializer.deserialize(any())).doReturn(
            KeyValuePairList(mutableListOf(KeyValuePair(EPOCH_KEY, EPOCH.toString())))
        )

        handler.invoke(requestContext, request)
        verify(entityManagerFactory).createEntityManager()
        verify(entityManagerFactory).close()
        verify(entityManager).transaction
        verify(registry).get(eq(CordaDb.Vault.persistenceUnitName))

        verify(keyValuePairListSerializer).serialize(
            KeyValuePairList(
                listOf(
                    KeyValuePair(EPOCH_KEY, "2"),
                    KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                    KeyValuePair("corda.notary.service.0.keys.0", "test-key"),
                    KeyValuePair("corda.notary.service.0.name", KNOWN_NOTARY_SERVICE),
                    KeyValuePair("corda.notary.service.0.plugin", KNOWN_NOTARY_PLUGIN),
                )
            )
        )
        with(argumentCaptor<Any>()) {
            verify(entityManager).persist(capture())
            assertThat(firstValue).isInstanceOf(GroupParametersEntity::class.java)
            val entity = firstValue as GroupParametersEntity
            assertThat(entity.epoch).isEqualTo(EPOCH + 1)
            assertThat(entity.signaturePublicKey).isEqualTo("test-key".toByteArray())
            assertThat(entity.signatureContent).isEqualTo(byteArrayOf(1))
        }
    }

    @Test
    fun `invoke with notary keys adds keys to existing notary service`() {
        val knownKey = mock<MemberNotaryKey> {
            on { publicKey } doReturn mock()
        }
        val notaryDetails = mock<MemberNotaryDetails> {
            on { keys } doReturn listOf(knownKey)
            on { serviceName } doReturn MemberX500Name.parse(KNOWN_NOTARY_SERVICE)
            on { servicePlugin } doReturn KNOWN_NOTARY_PLUGIN
        }
        val memberContext: MemberContext = mock {
            on { entries } doReturn mapOf("$ROLES_PREFIX.0" to NOTARY_ROLE).entries
            on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn notaryDetails
        }
        val mgmContext: MGMContext = mock {
            on { entries } doReturn mapOf("a" to "b").entries
        }
        val notaryInRequest: MemberInfo = mock {
            on { memberProvidedContext } doReturn memberContext
            on { mgmProvidedContext } doReturn mgmContext
        }
        val memberInfoFactory = mock<MemberInfoFactory> {
            on { create(any()) } doReturn notaryInRequest
        }
        whenever(persistenceHandlerServices.memberInfoFactory).doReturn(memberInfoFactory)
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn knownIdentity
        }
        val persistentNotary = PersistentMemberInfo(
            knownIdentity,
            notaryInRequest.memberProvidedContext.toWire(),
            notaryInRequest.mgmProvidedContext.toWire()
        )
        val request = mock<AddNotaryToGroupParameters> {
            on { notary } doReturn persistentNotary
        }
        whenever(keyValuePairListDeserializer.deserialize(any())).doReturn(
            KeyValuePairList(
                mutableListOf(
                    KeyValuePair(EPOCH_KEY, EPOCH.toString()),
                    KeyValuePair("corda.notary.service.5.name", KNOWN_NOTARY_SERVICE),
                    KeyValuePair("corda.notary.service.5.plugin", KNOWN_NOTARY_PLUGIN),
                    KeyValuePair("corda.notary.service.5.keys.0", "existing-test-key"),
                )
            )
        )

        handler.invoke(requestContext, request)
        verify(entityManagerFactory).createEntityManager()
        verify(entityManagerFactory).close()
        verify(entityManager).transaction
        verify(registry).get(eq(CordaDb.Vault.persistenceUnitName))

        verify(keyValuePairListSerializer).serialize(
            KeyValuePairList(
                listOf(
                    KeyValuePair("corda.notary.service.5.name", KNOWN_NOTARY_SERVICE),
                    KeyValuePair("corda.notary.service.5.plugin", KNOWN_NOTARY_PLUGIN),
                    KeyValuePair("corda.notary.service.5.keys.0", "existing-test-key"),
                    KeyValuePair(EPOCH_KEY, "2"),
                    KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                    KeyValuePair("corda.notary.service.5.keys.1", "test-key"),
                )
            )
        )
        with(argumentCaptor<Any>()) {
            verify(entityManager).persist(capture())
            assertThat(firstValue).isInstanceOf(GroupParametersEntity::class.java)
            val entity = firstValue as GroupParametersEntity
            assertThat(entity.epoch).isEqualTo(EPOCH + 1)
            assertThat(entity.signaturePublicKey).isEqualTo("test-key".toByteArray())
            assertThat(entity.signatureContent).isEqualTo(byteArrayOf(1))
        }
    }

    @Test
    fun `invoke with nothing to add does nothing`() {
        val knownKey = mock<MemberNotaryKey> {
            on { publicKey } doReturn mock()
        }
        val notaryDetails = mock<MemberNotaryDetails> {
            on { keys } doReturn listOf(knownKey)
            on { serviceName } doReturn MemberX500Name.parse(KNOWN_NOTARY_SERVICE)
            on { servicePlugin } doReturn KNOWN_NOTARY_PLUGIN
        }
        val memberContext: MemberContext = mock {
            on { entries } doReturn mapOf("$ROLES_PREFIX.0" to NOTARY_ROLE).entries
            on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn notaryDetails
        }
        val mgmContext: MGMContext = mock {
            on { entries } doReturn mapOf("a" to "b").entries
        }
        val notaryInRequest: MemberInfo = mock {
            on { memberProvidedContext } doReturn memberContext
            on { mgmProvidedContext } doReturn mgmContext
        }
        val memberInfoFactory = mock<MemberInfoFactory> {
            on { create(any()) } doReturn notaryInRequest
        }
        whenever(persistenceHandlerServices.memberInfoFactory).doReturn(memberInfoFactory)
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn knownIdentity
        }
        val persistentNotary = PersistentMemberInfo(
            knownIdentity,
            notaryInRequest.memberProvidedContext.toWire(),
            notaryInRequest.mgmProvidedContext.toWire()
        )
        val request = mock<AddNotaryToGroupParameters> {
            on { notary } doReturn persistentNotary
        }
        val mockGroupParameters = KeyValuePairList(
            mutableListOf(
                KeyValuePair(EPOCH_KEY, EPOCH.toString()),
                KeyValuePair("corda.notary.service.5.name", KNOWN_NOTARY_SERVICE),
                KeyValuePair("corda.notary.service.5.plugin", KNOWN_NOTARY_PLUGIN),
                KeyValuePair("corda.notary.service.5.keys.0", "test-key")
            )
        )
        whenever(keyValuePairListDeserializer.deserialize(any())).doReturn(mockGroupParameters)

        handler.invoke(requestContext, request)
        verify(entityManagerFactory).createEntityManager()
        verify(entityManagerFactory).close()
        verify(entityManager).transaction
        verify(registry).get(eq(CordaDb.Vault.persistenceUnitName))
        verify(entityManager, times(0)).persist(any())
    }

    @Test
    fun `notary plugin must be specified to add new notary service`() {
        val knownKey = mock<MemberNotaryKey> {
            on { publicKey } doReturn mock()
        }
        val notaryDetails = mock<MemberNotaryDetails> {
            on { keys } doReturn listOf(knownKey)
            on { serviceName } doReturn MemberX500Name.parse(KNOWN_NOTARY_SERVICE)
        }
        val memberContext: MemberContext = mock {
            on { entries } doReturn mapOf("$ROLES_PREFIX.0" to NOTARY_ROLE).entries
            on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn notaryDetails
        }
        val mgmContext: MGMContext = mock {
            on { entries } doReturn mapOf("a" to "b").entries
        }
        val notaryInRequest: MemberInfo = mock {
            on { memberProvidedContext } doReturn memberContext
            on { mgmProvidedContext } doReturn mgmContext
        }
        val memberInfoFactory = mock<MemberInfoFactory> {
            on { create(any()) } doReturn notaryInRequest
        }
        whenever(persistenceHandlerServices.memberInfoFactory).doReturn(memberInfoFactory)
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn knownIdentity
        }
        val persistentNotary = PersistentMemberInfo(
            knownIdentity,
            notaryInRequest.memberProvidedContext.toWire(),
            notaryInRequest.mgmProvidedContext.toWire()
        )
        val request = mock<AddNotaryToGroupParameters> {
            on { notary } doReturn persistentNotary
        }
        whenever(keyValuePairListDeserializer.deserialize(any())).doReturn(
            KeyValuePairList(mutableListOf(KeyValuePair(EPOCH_KEY, EPOCH.toString())))
        )

        val ex = assertFailsWith<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        assertThat(ex.message).contains("plugin must be specified")
    }

    @Test
    fun `notary plugin type must match that of existing notary service`() {
        val knownKey = mock<MemberNotaryKey> {
            on { publicKey } doReturn mock()
        }
        val notaryDetails = mock<MemberNotaryDetails> {
            on { keys } doReturn listOf(knownKey)
            on { serviceName } doReturn MemberX500Name.parse(KNOWN_NOTARY_SERVICE)
            on { servicePlugin } doReturn "incorrect.plugin.type"
        }
        val memberContext: MemberContext = mock {
            on { entries } doReturn mapOf("$ROLES_PREFIX.0" to NOTARY_ROLE).entries
            on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn notaryDetails
        }
        val mgmContext: MGMContext = mock {
            on { entries } doReturn mapOf("a" to "b").entries
        }
        val notaryInRequest: MemberInfo = mock {
            on { memberProvidedContext } doReturn memberContext
            on { mgmProvidedContext } doReturn mgmContext
        }
        val memberInfoFactory = mock<MemberInfoFactory> {
            on { create(any()) } doReturn notaryInRequest
        }
        whenever(persistenceHandlerServices.memberInfoFactory).doReturn(memberInfoFactory)
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn knownIdentity
        }
        val persistentNotary = PersistentMemberInfo(
            knownIdentity,
            notaryInRequest.memberProvidedContext.toWire(),
            notaryInRequest.mgmProvidedContext.toWire()
        )
        val request = mock<AddNotaryToGroupParameters> {
            on { notary } doReturn persistentNotary
        }
        whenever(keyValuePairListDeserializer.deserialize(any())).doReturn(
            KeyValuePairList(
                mutableListOf(
                    KeyValuePair(EPOCH_KEY, EPOCH.toString()),
                    KeyValuePair("corda.notary.service.5.name", KNOWN_NOTARY_SERVICE),
                    KeyValuePair("corda.notary.service.5.plugin", KNOWN_NOTARY_PLUGIN),
                )
            )
        )

        val ex = assertFailsWith<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        assertThat(ex.message).contains("plugin types do not match")
    }

    @Test
    fun `exception is thrown when there is no group parameters data in the database`() {
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn knownIdentity
        }
        val request: AddNotaryToGroupParameters = mock()
        val previousEntry: TypedQuery<GroupParametersEntity> = mock {
            on { resultList } doReturn emptyList()
        }
        val groupParametersQuery: TypedQuery<GroupParametersEntity> = mock {
            on { setMaxResults(1) } doReturn previousEntry
            on { setLockMode(LockModeType.PESSIMISTIC_WRITE) } doReturn mock
        }
        whenever(entityManager.createQuery(eq(query))).doReturn(groupParametersQuery)
        val ex = assertFailsWith<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        assertThat(ex.message).contains("no group parameters found")
    }

    @Test
    fun `exception is thrown when no notary details were provided`() {
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn knownIdentity
        }
        val persistentNotaryInfo: PersistentMemberInfo = mock()
        val request = mock<AddNotaryToGroupParameters> {
            on { notary } doReturn persistentNotaryInfo
        }
        val notaryInfo: MemberInfo = mock {
            on { memberProvidedContext } doReturn mock()
        }
        val memberInfoFactory = mock<MemberInfoFactory> {
            on { create(any()) } doReturn notaryInfo
        }
        whenever(keyValuePairListDeserializer.deserialize(any())).doReturn(KeyValuePairList(emptyList()))
        whenever(persistenceHandlerServices.memberInfoFactory).doReturn(memberInfoFactory)

        val ex = assertFailsWith<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        assertThat(ex.message).contains("notary details not found")
    }
}

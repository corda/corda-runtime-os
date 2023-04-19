package net.corda.membership.impl.persistence.service.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureSpecs
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
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MODIFIED_TIME_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_KEYS_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_NAME_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_PROTOCOL_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
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
import java.util.SortedMap
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
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root
import kotlin.test.assertFailsWith

class AddNotaryToGroupParametersHandlerTest {
    private companion object {
        const val EPOCH = 1
        const val KNOWN_NOTARY_SERVICE = "O=NotaryA, L=LDN, C=GB"
        const val KNOWN_NOTARY_PROTOCOL = "net.corda.notary.MyNotaryService"
    }

    private val knownIdentity = HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group")
    private val context = byteArrayOf(1, 2, 3)
    private val serializeCaptor = argumentCaptor<KeyValuePairList>()
    private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(serializeCaptor.capture()) } doReturn context
    }
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(any()) } doReturn KeyValuePairList(listOf(KeyValuePair(EPOCH_KEY, EPOCH.toString())))
    }
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
    private val previousGroupParameters = "test".toByteArray()
    private val previousEntry: TypedQuery<GroupParametersEntity> = mock {
        on { resultList } doReturn resultList
        on { singleResult } doReturn GroupParametersEntity(
            epoch = EPOCH,
            parameters = previousGroupParameters,
            signaturePublicKey = byteArrayOf(0),
            signatureContent = byteArrayOf(1),
            signatureSpec = SignatureSpecs.ECDSA_SHA256.signatureName
        )
    }
    private val groupParametersQuery: TypedQuery<GroupParametersEntity> = mock {
        on { setLockMode(LockModeType.PESSIMISTIC_WRITE) } doReturn mock
        on { setMaxResults(1) } doReturn previousEntry
    }
    private val membersQuery: TypedQuery<MemberInfoEntity> = mock {
        on { setLockMode(LockModeType.PESSIMISTIC_WRITE) } doReturn mock
        on { resultList } doReturn emptyList()
    }
    private val root = mock<Root<GroupParametersEntity>> {
        on { get<String>("epoch") } doReturn mock<Path<String>>()
    }
    private val status = mock<Path<String>>()
    private val memberX500Name = mock<Path<String>>()
    private val memberRoot = mock<Root<MemberInfoEntity>> {
        on { get<String>("status") } doReturn status
        on { get<String>("memberX500Name") } doReturn memberX500Name
    }
    private val order = mock<Order>()
    private val query = mock<CriteriaQuery<GroupParametersEntity>> {
        on { from(GroupParametersEntity::class.java) } doReturn root
        on { select(root) } doReturn mock
        on { orderBy(order) } doReturn mock
    }
    private val notEqualPredicate = mock<Predicate>()
    private val equalPredicate = mock<Predicate>()
    private val memberCriteriaQuery = mock<CriteriaQuery<MemberInfoEntity>> {
        on { from(MemberInfoEntity::class.java) } doReturn memberRoot
        on { select(memberRoot) } doReturn mock
        on { where(equalPredicate, notEqualPredicate) } doReturn mock
    }
    private val criteriaBuilder = mock<CriteriaBuilder> {
        on { createQuery(GroupParametersEntity::class.java) } doReturn query
        on { createQuery(MemberInfoEntity::class.java) } doReturn memberCriteriaQuery
        on { desc(any()) } doReturn order
        on { equal(status, MEMBER_STATUS_ACTIVE) } doReturn equalPredicate
        on { notEqual(memberX500Name, knownIdentity.x500Name.toString())} doReturn notEqualPredicate
    }
    private val entityManager = mock<EntityManager> {
        on { persist(any<GroupParametersEntity>()) } doAnswer {}
        on { criteriaBuilder } doReturn criteriaBuilder
        on { createQuery(eq(query)) } doReturn groupParametersQuery
        on { createQuery(eq(memberCriteriaQuery)) } doReturn membersQuery
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
    private val knownKey = mock<MemberNotaryKey> {
        on { publicKey } doReturn mock()
    }
    private val notaryDetails = mock<MemberNotaryDetails> {
        on { keys } doReturn listOf(knownKey)
        on { serviceName } doReturn MemberX500Name.parse(KNOWN_NOTARY_SERVICE)
        on { serviceProtocol } doReturn KNOWN_NOTARY_PROTOCOL
        on { serviceProtocolVersions } doReturn setOf(1, 3)
    }
    private val notaryMemberContext: MemberContext = mock {
        on { entries } doReturn mapOf("$ROLES_PREFIX.0" to NOTARY_ROLE).entries
        on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn notaryDetails
    }
    private val notaryMgmContext: MGMContext = mock {
        on { entries } doReturn mapOf("a" to "b").entries
        on { parse(eq(STATUS), eq(String::class.java)) } doReturn MEMBER_STATUS_ACTIVE
    }
    private val notaryInRequest: MemberInfo = mock {
        on { name } doReturn MemberX500Name.parse(knownIdentity.x500Name)
        on { memberProvidedContext } doReturn notaryMemberContext
        on { mgmProvidedContext } doReturn notaryMgmContext
    }
    private val memberInfoFactory = mock<MemberInfoFactory> {
        on { create(any()) } doReturn notaryInRequest
    }
    private val requestContext = mock<MembershipRequestContext> {
        on { holdingIdentity } doReturn knownIdentity
    }
    private val persistentNotary = PersistentMemberInfo(
        knownIdentity,
        notaryInRequest.memberProvidedContext.toWire(),
        notaryInRequest.mgmProvidedContext.toWire()
    )
    private val request = mock<AddNotaryToGroupParameters> {
        on { notary } doReturn persistentNotary
    }
    private lateinit var otherNotaryDetails: MemberNotaryDetails
    private lateinit var otherNotaryContext: MemberContext
    private lateinit var otherNotaryEntity: MemberInfoEntity
    private lateinit var otherNotary: MemberInfo
    private val persistenceHandlerServices = mock<PersistenceHandlerServices> {
        on { cordaAvroSerializationFactory } doReturn serializationFactory
        on { virtualNodeInfoReadService } doReturn nodeInfoReadService
        on { jpaEntitiesRegistry } doReturn registry
        on { dbConnectionManager } doReturn connectionManager
        on { clock } doReturn clock
        on { keyEncodingService } doReturn keyEncodingService
        on { memberInfoFactory } doReturn memberInfoFactory
    }
    private val handler = AddNotaryToGroupParametersHandler(persistenceHandlerServices)

    private fun mockExistingNotary() {
        otherNotaryDetails = mock {
            on { keys } doReturn listOf(knownKey)
            on { serviceName } doReturn MemberX500Name.parse(KNOWN_NOTARY_SERVICE)
            on { serviceProtocol } doReturn KNOWN_NOTARY_PROTOCOL
            on { serviceProtocolVersions } doReturn setOf(1, 2)
        }
        otherNotaryContext = mock {
            on { entries } doReturn mapOf("$ROLES_PREFIX.0" to NOTARY_ROLE).entries
            on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn otherNotaryDetails
        }
        otherNotaryEntity = mock {
            on { memberContext } doReturn context
            on { mgmContext } doReturn context
        }
        otherNotary = mock {
            on { memberProvidedContext } doReturn otherNotaryContext
            on { mgmProvidedContext } doReturn notaryMgmContext
            on { name } doReturn MemberX500Name.parse("O=Bob,L=London,C=GB")
        }
        whenever(memberInfoFactory.create(any(), any<SortedMap<String, String?>>())).doReturn(otherNotary)
        whenever(membersQuery.resultList).doReturn(listOf(otherNotaryEntity))
        whenever(keyValuePairListDeserializer.deserialize(any())).doReturn(
            KeyValuePairList(listOf(
                KeyValuePair(EPOCH_KEY, EPOCH.toString()),
                KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 5), KNOWN_NOTARY_SERVICE),
                KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 5), KNOWN_NOTARY_PROTOCOL),
                KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 0), "1"),
                KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 5, 0), "existing-test-key"),
            ).sorted())
        )
    }

    @Test
    fun `invoke with new notary service name adds new notary service`() {
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
                    KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 0, 0), "test-key"),
                    KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 0), KNOWN_NOTARY_SERVICE),
                    KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 0), KNOWN_NOTARY_PROTOCOL),
                    KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 0), "1"),
                    KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 1), "3"),
                )
            )
        )
        with(argumentCaptor<Any>()) {
            verify(entityManager).persist(capture())
            assertThat(firstValue).isInstanceOf(GroupParametersEntity::class.java)
            val entity = firstValue as GroupParametersEntity
            assertThat(entity.epoch).isEqualTo(EPOCH + 1)
            assertThat(entity.signaturePublicKey).isNull()
            assertThat(entity.signatureContent).isNull()
            assertThat(entity.signatureSpec).isNull()
        }
    }

    @Test
    fun `invoke with notary keys adds keys to existing notary service`() {
        mockExistingNotary()

        handler.invoke(requestContext, request)

        verify(entityManagerFactory).createEntityManager()
        verify(entityManagerFactory).close()
        verify(entityManager).transaction
        verify(registry).get(eq(CordaDb.Vault.persistenceUnitName))
        verify(keyValuePairListSerializer).serialize(
            KeyValuePairList(
                listOf(
                    KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 5), KNOWN_NOTARY_PROTOCOL),
                    KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 5, 0), "existing-test-key"),
                    KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 5), KNOWN_NOTARY_SERVICE),
                    KeyValuePair(EPOCH_KEY, "2"),
                    KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                    KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 5, 1), "test-key"),
                    KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 0), "1"),
                )
            )
        )
        with(argumentCaptor<Any>()) {
            verify(entityManager).persist(capture())
            assertThat(firstValue).isInstanceOf(GroupParametersEntity::class.java)
            val entity = firstValue as GroupParametersEntity
            assertThat(entity.epoch).isEqualTo(EPOCH + 1)
            assertThat(entity.signaturePublicKey).isNull()
            assertThat(entity.signatureContent).isNull()
            assertThat(entity.signatureSpec).isNull()
        }
    }

    @Test
    fun `invoke sets notary protocol versions to intersection of protocol versions of individual notary vnodes`() {
        mockExistingNotary()

        handler.invoke(requestContext, request)

        assertThat(serializeCaptor.firstValue.items)
            .contains(KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 0), "1"))
            .doesNotContain(KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 1), "2"))
            .doesNotContain(KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 1), "3"))
    }

    @Test
    fun `invoke with nothing to add does nothing`() {
        mockExistingNotary()
        val mockGroupParameters = KeyValuePairList(
            listOf(
                KeyValuePair(EPOCH_KEY, EPOCH.toString()),
                KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 5), KNOWN_NOTARY_SERVICE),
                KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 5), KNOWN_NOTARY_PROTOCOL),
                KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 0), "1"),
                KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 5, 0), "test-key")
            ).sorted()
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
    fun `notary protocol must be specified to add new notary service`() {
        whenever(notaryDetails.serviceProtocol).doReturn(null)

        val ex = assertFailsWith<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        assertThat(ex.message).contains("protocol must be specified")
    }

    @Test
    fun `notary protocol must match that of existing notary service`() {
        mockExistingNotary()
        whenever(notaryDetails.serviceProtocol).doReturn("incorrect.plugin.type")

        val ex = assertFailsWith<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        assertThat(ex.message).contains("protocols do not match")
    }

    @Test
    fun `exception is thrown if notary protocol is specified but versions are missing`() {
        whenever(notaryDetails.serviceProtocol).doReturn(KNOWN_NOTARY_PROTOCOL)
        whenever(notaryDetails.serviceProtocolVersions).doReturn(emptySet())

        val ex = assertFailsWith<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        assertThat(ex.message).contains("protocol versions are missing")
    }

    @Test
    fun `exception is thrown when there is no group parameters data in the database`() {
        whenever(previousEntry.resultList).doReturn(emptyList())

        val ex = assertFailsWith<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        assertThat(ex.message).contains("no group parameters found")
    }

    @Test
    fun `exception is thrown when no notary details were provided`() {
        whenever(notaryInRequest.notaryDetails).doReturn(null)

        val ex = assertFailsWith<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        assertThat(ex.message).contains("notary details not found")
    }
}

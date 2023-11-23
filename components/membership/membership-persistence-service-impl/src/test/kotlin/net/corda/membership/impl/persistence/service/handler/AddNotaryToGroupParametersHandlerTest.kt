package net.corda.membership.impl.persistence.service.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedData
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.AddNotaryToGroupParameters
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MODIFIED_TIME_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_BACKCHAIN_REQUIRED
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
import org.junit.jupiter.api.Disabled
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
import java.nio.ByteBuffer
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
    private val otherName = MemberX500Name.parse("O=Other,L=London,C=GB")
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
    private val statusPath = mock<Path<String>>()
    private val memberX500NamePath = mock<Path<String>>()
    private val memberRoot = mock<Root<MemberInfoEntity>> {
        on { get<String>("status") } doReturn statusPath
        on { get<String>("memberX500Name") } doReturn memberX500NamePath
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
        on { where(any(), any()) } doReturn mock
    }
    private val criteriaBuilder = mock<CriteriaBuilder> {
        on { createQuery(GroupParametersEntity::class.java) } doReturn query
        on { createQuery(MemberInfoEntity::class.java) } doReturn memberCriteriaQuery
        on { desc(any()) } doReturn order
        on { equal(statusPath, MEMBER_STATUS_ACTIVE) } doReturn equalPredicate
        on { notEqual(eq(memberX500NamePath), any<String>()) } doReturn notEqualPredicate
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
    private val dbConnectionManager = mock<DbConnectionManager> {
        on {
            getOrCreateEntityManagerFactory(
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
        on { name } doReturn MemberX500Name.parse(knownIdentity.x500Name)
        on { serial } doReturn 2L
    }
    private val memberInfoFactory = mock<MemberInfoFactory> {
        on { createMemberInfo(any()) } doReturn notaryInRequest
    }
    private val requestContext = mock<MembershipRequestContext> {
        on { holdingIdentity } doReturn knownIdentity
    }
    private val persistentNotary = PersistentMemberInfo(
        knownIdentity,
        null,
        null,
        SignedData(
            ByteBuffer.wrap(byteArrayOf(1)),
            CryptoSignatureWithKey(
                ByteBuffer.wrap(byteArrayOf(2)),
                ByteBuffer.wrap(byteArrayOf(2)),
            ),
            CryptoSignatureSpec("", null, null),
        ),
        ByteBuffer.wrap(byteArrayOf(3)),
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
        on { dbConnectionManager } doReturn dbConnectionManager
        on { clock } doReturn clock
        on { keyEncodingService } doReturn keyEncodingService
        on { memberInfoFactory } doReturn memberInfoFactory
        on { transactionTimerFactory } doReturn { transactionTimer }
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
            on { name } doReturn MemberX500Name.parse(knownIdentity.x500Name)
            on { serial } doReturn 1L
        }
        whenever(memberInfoFactory.createMemberInfo(any(), any<SortedMap<String, String?>>())).doReturn(otherNotary)
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
                    KeyValuePair(String.format(NOTARY_SERVICE_BACKCHAIN_REQUIRED, 0), "false"),
                    KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 0), "1"),
                    KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 1), "3")
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
    fun `invoke as re-registration with notary keys adds keys to existing notary service`() {
        mockExistingNotary()

        handler.invoke(requestContext, request)

        verify(entityManagerFactory).createEntityManager()
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
                    KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 1), "3"),
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
    fun `existing notary service can only be updated in case of re-registration`() {
        mockExistingNotary()
        whenever(otherNotary.name).doReturn(otherName)

        val ex = assertFailsWith<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        assertThat(ex.message).contains("already exists")
    }

    @Test
    fun `notary to be added cannot have same service name as an existing virtual node`() {
        mockExistingNotary()
        whenever(otherNotary.name).doReturn(otherName)
        whenever(notaryDetails.serviceName).doReturn(otherName)

        val ex = assertFailsWith<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        assertThat(ex.message).contains("virtual node having the same name")
    }

    @Test
    @Disabled("Until CORE-12934 adds support for multiple notary virtual nodes per notary service")
    fun `invoke sets notary protocol versions to intersection of protocol versions of individual notary vnodes`() {
        mockExistingNotary()

        handler.invoke(requestContext, request)

        assertThat(serializeCaptor.firstValue.items)
            .contains(KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 0), "1"))
            .doesNotContain(KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 1), "2"))
            .doesNotContain(KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 1), "3"))
    }

    @Test
    fun `invoke as re-registration with nothing to add does nothing`() {
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
        verify(entityManager).transaction
        verify(registry).get(eq(CordaDb.Vault.persistenceUnitName))
        verify(entityManager, times(0)).persist(any())
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

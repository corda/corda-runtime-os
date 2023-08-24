package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryRegistrationRequest
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.TypedQuery
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Path
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root

class QueryRegistrationRequestHandlerTest {
    private val holdingIdentity = HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "groupId")
    private val shortHash = holdingIdentity.toCorda().shortHash
    private val serialisedMemberContext = byteArrayOf(1, 2, 3)
    private val memberContext = mock<KeyValuePairList>()
    private val memberSignatureKey = byteArrayOf(1)
    private val memberSignatureContent = byteArrayOf(2)
    private val memberSignatureSpec = "SignatureSpec"
    private val serialisedRegistrationContext = "serialisedRegistrationContext".toByteArray()
    private val registrationSignatureKey = "registrationSignatureKey".toByteArray()
    private val registrationSignatureContent = "registrationSignatureContent".toByteArray()
    private val registrationContextSignatureSpec = "SignatureSpec-2"
    private val entitySet = mock<JpaEntitiesSet>()
    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry> {
        on { get(CordaDb.Vault.persistenceUnitName) } doReturn entitySet
    }
    private val entityTransaction = mock<EntityTransaction>()
    private val registrationIdPath = mock<Path<String>>()
    private val registrationId = "id"
    private val root = mock<Root<RegistrationRequestEntity>> {
        on { get<String>("registrationId") } doReturn registrationIdPath
    }
    private val predicate = mock<Predicate>()
    private val query = mock<CriteriaQuery<RegistrationRequestEntity>> {
        on { from(RegistrationRequestEntity::class.java) } doReturn root
        on { select(root) } doReturn mock
        on { where(predicate) } doReturn mock
    }
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(serialisedMemberContext) } doReturn memberContext
    }
    private val serializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn keyValuePairListDeserializer
    }

    private val criteriaBuilder = mock<CriteriaBuilder> {
        on { createQuery(RegistrationRequestEntity::class.java) } doReturn query
        on { equal(registrationIdPath, registrationId) } doReturn predicate
    }
    private val actualQuery = mock<TypedQuery<RegistrationRequestEntity>>()
    private val entityManager = mock<EntityManager> {
        on { transaction } doReturn entityTransaction
        on { criteriaBuilder } doReturn criteriaBuilder
        on { createQuery(query) } doReturn actualQuery
    }
    private val entityManagerFactory = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn entityManager
    }
    private val dbConnectionManager = mock<DbConnectionManager> {
        on { getOrCreateEntityManagerFactory(any<UUID>(), any()) } doReturn entityManagerFactory
    }
    private val nodeInfo = mock<VirtualNodeInfo> {
        on { vaultDmlConnectionId } doReturn UUID(0, 0)
    }
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(holdingIdentity.toCorda().shortHash) } doReturn nodeInfo
    }
    private val service = mock<PersistenceHandlerServices> {
        on { virtualNodeInfoReadService } doReturn virtualNodeInfoReadService
        on { dbConnectionManager } doReturn dbConnectionManager
        on { jpaEntitiesRegistry } doReturn jpaEntitiesRegistry
        on { cordaAvroSerializationFactory } doReturn serializationFactory
        on { transactionTimerFactory } doReturn { transactionTimer }
    }
    private val context = mock<MembershipRequestContext> {
        on { holdingIdentity } doReturn holdingIdentity
    }
    val request = QueryRegistrationRequest(
        registrationId
    )

    private val handler = QueryRegistrationRequestHandler(service)

    @Test
    fun `invoke return the correct response when entity was found`() {
        whenever(actualQuery.resultList).doReturn(
            listOf(
                RegistrationRequestEntity(
                    registrationId,
                    shortHash.value,
                    "SENT_TO_MGM",
                    Instant.ofEpochSecond(500),
                    Instant.ofEpochSecond(600),
                    serialisedMemberContext,
                    memberSignatureKey,
                    memberSignatureContent,
                    memberSignatureSpec,
                    serialisedRegistrationContext,
                    registrationSignatureKey,
                    registrationSignatureContent,
                    registrationContextSignatureSpec,
                    0L,
                    "test reason"
                )
            )
        )

        val result = handler.invoke(context, request)

        assertThat(result.registrationRequest?.registrationId)
            .isNotNull
            .isEqualTo(registrationId)
        assertThat(result.registrationRequest.memberProvidedContext.data.array()).isEqualTo(serialisedMemberContext)
        assertThat(result.registrationRequest.memberProvidedContext.signature).isEqualTo(
            CryptoSignatureWithKey(
                ByteBuffer.wrap(memberSignatureKey),
                ByteBuffer.wrap(memberSignatureContent)
            )
        )
        assertThat(result.registrationRequest.memberProvidedContext.signatureSpec).isEqualTo(
            CryptoSignatureSpec(memberSignatureSpec, null, null)
        )
        assertThat(result.registrationRequest.registrationContext.data.array()).isEqualTo(serialisedRegistrationContext)
        assertThat(result.registrationRequest.registrationContext.signature).isEqualTo(
            CryptoSignatureWithKey(
                ByteBuffer.wrap(registrationSignatureKey),
                ByteBuffer.wrap(registrationSignatureContent)
            )
        )
        assertThat(result.registrationRequest.registrationContext.signatureSpec).isEqualTo(
            CryptoSignatureSpec(registrationContextSignatureSpec, null, null)
        )
    }

    @Test
    fun `invoke return empty response when entity was not found`() {
        whenever(actualQuery.resultList).doReturn(
            emptyList()
        )

        val result = handler.invoke(context, request)

        assertThat(result.registrationRequest)
            .isNull()
    }
}

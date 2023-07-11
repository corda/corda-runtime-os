package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.SignedGroupParameters
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.UpdateGroupParameters
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.test.util.time.TestClock
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
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

class UpdateGroupParametersHandlerTest {
    private companion object {
        const val TEST_EPOCH = 1
    }

    private val serializedParams = "group-parameters-1".toByteArray()
    private val deserializedParams = KeyValuePairList(listOf(KeyValuePair(EPOCH_KEY, TEST_EPOCH.toString())))

    private val serializedNewParams = "group-parameters-2".toByteArray()

    private val sigPubKey = "public-key".toByteArray()
    private val sigContent = "signature-bytes".toByteArray()

    private val signatureSpec = CryptoSignatureSpec(
        SignatureSpecs.ECDSA_SHA256.signatureName, null, null
    )

    private val signedParams = SignedGroupParameters(
        ByteBuffer.wrap(serializedNewParams),
        null,
        null
    )

    private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(any()) } doReturn serializedNewParams
    }
    private val deserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(serializedParams) } doReturn deserializedParams
    }
    private val serializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn keyValuePairListSerializer
        on { createAvroDeserializer<KeyValuePairList>(any(), any()) } doReturn deserializer
    }
    private val entitySet = mock<JpaEntitiesSet>()
    private val registry = mock<JpaEntitiesRegistry> {
        on { get(CordaDb.Vault.persistenceUnitName) } doReturn entitySet
    }
    private val transaction = mock<EntityTransaction>()
    private val currentEntity = GroupParametersEntity(
        epoch = 1,
        parameters = serializedParams,
        signaturePublicKey = sigPubKey,
        signatureContent = sigContent,
        signatureSpec = signatureSpec.signatureName
    )
    private val resultList = listOf(currentEntity)
    private val currentEntry: TypedQuery<GroupParametersEntity> = mock {
        on { singleResult } doReturn currentEntity
        on { resultList } doReturn resultList
    }
    private val groupParametersQuery: TypedQuery<GroupParametersEntity> = mock {
        on { setLockMode(LockModeType.PESSIMISTIC_WRITE) } doReturn mock
        on { setMaxResults(1) } doReturn currentEntry
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
    private val entityCapture = argumentCaptor<GroupParametersEntity>()
    private val entityManager = mock<EntityManager> {
        on { persist(entityCapture.capture()) } doAnswer {}
        on { transaction } doReturn transaction
        on { criteriaBuilder } doReturn criteriaBuilder
        on { createQuery(eq(query)) } doReturn groupParametersQuery
    }
    private val entityManagerFactory = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn entityManager
    }
    private val connectionManager = mock<DbConnectionManager> {
        on {
            getOrCreateEntityManagerFactory(
                any(),
                any(),
                eq(entitySet),
            )
        } doReturn entityManagerFactory
    }
    private val clock = TestClock(Instant.ofEpochMilli(10))
    private val keyEncodingService: KeyEncodingService = mock {
        on { encodeAsByteArray(any()) } doReturn "test-key".toByteArray()
    }
    private val persistenceHandlerServices = mock<PersistenceHandlerServices> {
        on { cordaAvroSerializationFactory } doReturn serializationFactory
        on { jpaEntitiesRegistry } doReturn registry
        on { dbConnectionManager } doReturn connectionManager
        on { clock } doReturn clock
        on { keyEncodingService } doReturn keyEncodingService
        on { transactionTimerFactory } doReturn { transactionTimer }
    }
    private val identity = HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group").toCorda()
    private val context = mock<MembershipRequestContext> {
        on { holdingIdentity } doReturn identity.toAvro()
    }
    private val request = mock<UpdateGroupParameters> {
        on { update } doReturn mock()
    }

    private val handler = UpdateGroupParametersHandler(persistenceHandlerServices)

    @Test
    fun `invoke returns the correct version`() {
        val result = assertDoesNotThrow {
            handler.invoke(context, request)
        }
        assertThat(entityCapture.firstValue.epoch).isEqualTo(2)
        assertThat(result).isEqualTo(PersistGroupParametersResponse(signedParams))
    }

    @Test
    fun `invoke throws an exception if current parameters cannot be retrieved`() {
        whenever(currentEntry.resultList).doReturn(emptyList())

        assertThrows<MembershipPersistenceException> {
            handler.invoke(context, request)
        }
    }
}
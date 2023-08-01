package net.corda.membership.impl.persistence.service.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistGroupParametersInitialSnapshot
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MODIFIED_TIME_KEY
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.test.util.time.TestClock
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
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

class PersistGroupParametersInitialSnapshotHandlerTest {
    private companion object {
        const val SNAPSHOT_EPOCH = "1"
    }

    private val serializedParams = byteArrayOf(1, 2, 3)
    private val serializedSignatureContent = byteArrayOf(2)
    private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(any()) } doReturn serializedParams
    }
    private val deserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(serializedParams) } doReturn KeyValuePairList(emptyList())
        on { deserialize(serializedSignatureContent) } doReturn KeyValuePairList(emptyList())
    }
    private val serializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn keyValuePairListSerializer
        on { createAvroDeserializer<KeyValuePairList>(any(), any()) } doReturn deserializer
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
    private val entityManager = mock<EntityManager> {
        on { persist(any<GroupParametersEntity>()) } doAnswer {}
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
    private val keyEncodingService: KeyEncodingService = mock {
        on { encodeAsByteArray(any()) } doReturn "test-key".toByteArray()
    }
    private val persistenceHandlerServices = mock<PersistenceHandlerServices> {
        on { cordaAvroSerializationFactory } doReturn serializationFactory
        on { virtualNodeInfoReadService } doReturn nodeInfoReadService
        on { jpaEntitiesRegistry } doReturn registry
        on { dbConnectionManager } doReturn dbConnectionManager
        on { clock } doReturn clock
        on { keyEncodingService } doReturn keyEncodingService
        on { transactionTimerFactory } doReturn { transactionTimer }
    }
    private val handler = PersistGroupParametersInitialSnapshotHandler(persistenceHandlerServices)

    @Test
    fun `invoke return the correct version`() {
        val context = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group")
        }
        val request = mock<PersistGroupParametersInitialSnapshot>()

        handler.invoke(context, request)

        verify(keyValuePairListSerializer).serialize(
            KeyValuePairList(
                listOf(
                    KeyValuePair(EPOCH_KEY, SNAPSHOT_EPOCH),
                    KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
                )
            )
        )
    }

    @Test
    fun `invoke will do nothing if the initial snapshot is already persisted`() {
        val context = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group")
        }
        val request = mock<PersistGroupParametersInitialSnapshot>()
        val content = byteArrayOf(1, 2, 3)
        whenever(
            deserializer.deserialize(content)
        ).doReturn(
            KeyValuePairList(
                listOf(
                    KeyValuePair(EPOCH_KEY, "1"),
                    KeyValuePair(MODIFIED_TIME_KEY, (clock.instant().epochSecond + 5L).toString()),
                )
            )
        )
        whenever(
            entityManager.find(
                GroupParametersEntity::class.java,
                1,
                LockModeType.PESSIMISTIC_WRITE
            )
        ).doReturn(
            GroupParametersEntity(
                epoch = 1,
                parameters = content,
                signaturePublicKey = byteArrayOf(0),
                signatureContent = byteArrayOf(1),
                signatureSpec = SignatureSpecs.ECDSA_SHA256.signatureName
            )
        )

        handler.invoke(context, request)

        verify(entityManager, never()).persist(any())
    }

    @Test
    fun `invoke will throw an exception if saved snapshot is wrong`() {
        val context = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group")
        }
        val request = mock<PersistGroupParametersInitialSnapshot>()
        val content = byteArrayOf(1, 2, 3)
        whenever(
            deserializer.deserialize(content)
        ).doReturn(
            KeyValuePairList(
                emptyList()
            )
        )
        whenever(
            entityManager.find(
                GroupParametersEntity::class.java,
                1,
                LockModeType.PESSIMISTIC_WRITE
            )
        ).doReturn(
            GroupParametersEntity(
                epoch = 1,
                parameters = content,
                signaturePublicKey = byteArrayOf(0),
                signatureContent = byteArrayOf(1),
                signatureSpec = SignatureSpecs.ECDSA_SHA256.signatureName
            )
        )

        assertThrows<MembershipPersistenceException> {
            handler.invoke(context, request)
        }
    }
}

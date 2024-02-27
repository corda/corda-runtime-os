package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.StaticNetworkInfo
import net.corda.data.membership.db.request.command.UpdateStaticNetworkInfo
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.membership.datamodel.StaticNetworkInfoEntity
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MODIFIED_TIME_KEY
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.test.util.time.TestClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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

class UpdateStaticNetworkInfoHandlerTest {
    private val clock = TestClock(Instant.ofEpochSecond(1))
    private val entityTransaction = mock<EntityTransaction>()
    private val entityManager = mock<EntityManager> {
        on { transaction } doReturn entityTransaction
    }
    private val entityManagerFactory = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn entityManager
    }
    private val dbConnectionManager = mock<DbConnectionManager> {
        on { getClusterEntityManagerFactory() } doReturn entityManagerFactory
    }

    private val existingSerializedParams = "group-parameters-1".toByteArray()
    private val existingDeserializedParams = KeyValuePairList(
        listOf(
            KeyValuePair(EPOCH_KEY, "1"),
            KeyValuePair(
                MODIFIED_TIME_KEY,
                clock.apply {
                    setTime(Instant.ofEpochSecond(100))
                }.instant().toString()
            )
        )
    )
    private val groupId = UUID(0, 1).toString()
    private val serializedPubKey = "bytes1".toByteArray()
    private val serializedPrivateKey = "bytes2".toByteArray()
    private val existingStaticNetworkInfo = StaticNetworkInfoEntity(
        groupId,
        serializedPubKey,
        serializedPrivateKey,
        existingSerializedParams
    )

    private val proposedSerializedParams = "group-parameters-2".toByteArray()
    private val proposedDeserializedParams = KeyValuePairList(
        listOf(
            KeyValuePair(EPOCH_KEY, "2"),
            KeyValuePair(
                MODIFIED_TIME_KEY,
                clock.apply {
                    setTime(Instant.ofEpochSecond(200))
                }.instant().toString()
            )
        )
    )

    private val proposedUnchangedSerializedParams = "group-parameters-3".toByteArray()
    private val proposedUnchangedDeserializedParams = KeyValuePairList(
        listOf(
            KeyValuePair(EPOCH_KEY, "1"),
            KeyValuePair(
                MODIFIED_TIME_KEY,
                clock.apply {
                    setTime(Instant.ofEpochSecond(200))
                }.instant().toString()
            )
        )
    )

    private val deserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(existingSerializedParams) } doReturn existingDeserializedParams
        on { deserialize(proposedSerializedParams) } doReturn proposedDeserializedParams
        on { deserialize(proposedUnchangedSerializedParams) } doReturn proposedUnchangedDeserializedParams
    }
    private val serializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(proposedDeserializedParams) } doReturn proposedSerializedParams
        on { serialize(proposedUnchangedDeserializedParams) } doReturn proposedUnchangedSerializedParams
    }
    private val serializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn deserializer
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn serializer
    }

    private val services = mock<PersistenceHandlerServices> {
        on { dbConnectionManager } doReturn dbConnectionManager
        on { cordaAvroSerializationFactory } doReturn serializationFactory
        on { transactionTimerFactory } doReturn { transactionTimer }
    }
    private val handler = UpdateStaticNetworkInfoHandler(services)

    @Test
    fun `New info can be successfully updated`() {
        whenever(
            entityManager.find(
                eq(StaticNetworkInfoEntity::class.java),
                eq(groupId),
                eq(LockModeType.PESSIMISTIC_WRITE)
            )
        ).doReturn(existingStaticNetworkInfo)

        val proposedStaticNetworkInfo = StaticNetworkInfo(
            existingStaticNetworkInfo.groupId,
            proposedDeserializedParams,
            ByteBuffer.wrap(existingStaticNetworkInfo.mgmPublicKey),
            ByteBuffer.wrap(existingStaticNetworkInfo.mgmPrivateKey),
            existingStaticNetworkInfo.version
        )

        val result = handler.invoke(
            mock(),
            UpdateStaticNetworkInfo(proposedStaticNetworkInfo)
        )

        assertThat(result.info.groupId).isEqualTo(groupId)
        assertThat(result.info.groupParameters).isEqualTo(proposedDeserializedParams)
        assertThat(result.info.mgmPublicSigningKey).isEqualTo(ByteBuffer.wrap(serializedPubKey))
        assertThat(result.info.mgmPrivateSigningKey).isEqualTo(ByteBuffer.wrap(serializedPrivateKey))

        val persistedEntityCaptor = argumentCaptor<StaticNetworkInfoEntity>()
        verify(entityManager).merge(persistedEntityCaptor.capture())
        verify(entityManager).flush()

        val persistedEntity = persistedEntityCaptor.firstValue
        assertThat(persistedEntity.groupId).isEqualTo(result.info.groupId)
        assertThat(persistedEntity.groupParameters).isEqualTo(proposedSerializedParams)
        assertThat(persistedEntity.version).isEqualTo(result.info.version)
    }

    @Test
    fun `exception thrown if no existing info found`() {
        whenever(
            entityManager.find(
                eq(StaticNetworkInfoEntity::class.java),
                eq(groupId),
                eq(LockModeType.PESSIMISTIC_WRITE)
            )
        ).doReturn(null)

        val proposedStaticNetworkInfo = StaticNetworkInfo(
            existingStaticNetworkInfo.groupId,
            proposedDeserializedParams,
            ByteBuffer.wrap(existingStaticNetworkInfo.mgmPublicKey),
            ByteBuffer.wrap(existingStaticNetworkInfo.mgmPrivateKey),
            existingStaticNetworkInfo.version
        )

        assertThrows<MembershipPersistenceException> {
            handler.invoke(
                mock(),
                UpdateStaticNetworkInfo(proposedStaticNetworkInfo)
            )
        }.also {
            assertThat(it.message).contains("No existing static network")
        }

        verify(entityManager, never()).merge(any<StaticNetworkInfoEntity>())
        verify(entityManager, never()).flush()
    }

    @Test
    fun `No error if proposed info has already been persisted so handler is tolerant to retries`() {
        whenever(
            entityManager.find(
                eq(StaticNetworkInfoEntity::class.java),
                eq(groupId),
                eq(LockModeType.PESSIMISTIC_WRITE)
            )
        ).doReturn(existingStaticNetworkInfo)

        val proposedStaticNetworkInfo = StaticNetworkInfo(
            existingStaticNetworkInfo.groupId,
            proposedUnchangedDeserializedParams,
            ByteBuffer.wrap(existingStaticNetworkInfo.mgmPublicKey),
            ByteBuffer.wrap(existingStaticNetworkInfo.mgmPrivateKey),
            existingStaticNetworkInfo.version - 1
        )

        val result = handler.invoke(
            mock(),
            UpdateStaticNetworkInfo(proposedStaticNetworkInfo)
        )

        assertThat(result.info.groupId).isEqualTo(groupId)
        assertThat(result.info.groupParameters).isEqualTo(existingDeserializedParams)
        assertThat(result.info.mgmPublicSigningKey).isEqualTo(ByteBuffer.wrap(serializedPubKey))
        assertThat(result.info.mgmPrivateSigningKey).isEqualTo(ByteBuffer.wrap(serializedPrivateKey))

        verify(entityManager, never()).merge(any<StaticNetworkInfoEntity>())
        verify(entityManager, never()).flush()
    }

    @Test
    fun `Exeption if proposed info version has already been persisted and parameters have changed`() {
        whenever(
            entityManager.find(
                eq(StaticNetworkInfoEntity::class.java),
                eq(groupId),
                eq(LockModeType.PESSIMISTIC_WRITE)
            )
        ).doReturn(existingStaticNetworkInfo)

        val proposedStaticNetworkInfo = StaticNetworkInfo(
            existingStaticNetworkInfo.groupId,
            proposedDeserializedParams,
            ByteBuffer.wrap(existingStaticNetworkInfo.mgmPublicKey),
            ByteBuffer.wrap(existingStaticNetworkInfo.mgmPrivateKey),
            existingStaticNetworkInfo.version - 1
        )

        assertThrows<MembershipPersistenceException> {
            handler.invoke(
                mock(),
                UpdateStaticNetworkInfo(proposedStaticNetworkInfo)
            )
        }.also {
            assertThat(it.message).contains("persisted version of the static network information does not match")
        }

        verify(entityManager, never()).merge(any<StaticNetworkInfoEntity>())
        verify(entityManager, never()).flush()
    }
}

package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.query.QueryStaticNetworkInfo
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.membership.datamodel.StaticNetworkInfoEntity
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

class QueryStaticNetworkInfoHandlerTest {
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

    private val serializedParams = "group-parameters-1".toByteArray()
    private val deserializedParams = KeyValuePairList(listOf(KeyValuePair(EPOCH_KEY, "1")))

    private val deserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(serializedParams) } doReturn deserializedParams
    }
    private val serializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn deserializer
    }

    private val services = mock<PersistenceHandlerServices> {
        on { dbConnectionManager } doReturn dbConnectionManager
        on { cordaAvroSerializationFactory } doReturn serializationFactory
        on { transactionTimerFactory } doReturn { transactionTimer }
    }
    private val handler = QueryStaticNetworkInfoHandler(services)

    private val groupId = UUID(0, 1).toString()
    private val serializedPubKey = "bytes1".toByteArray()
    private val serializedPrivateKey = "bytes2".toByteArray()

    @Test
    fun `Can be called successfully`() {
        whenever(entityManager.find(eq(StaticNetworkInfoEntity::class.java), eq(groupId))).doReturn(
            StaticNetworkInfoEntity(
                groupId,
                serializedPubKey,
                serializedPrivateKey,
                serializedParams,
            )
        )

        val result = handler.invoke(mock(), QueryStaticNetworkInfo(groupId))

        assertThat(result.info.version).isEqualTo(1)
        assertThat(result.info.groupId).isEqualTo(groupId)
        assertThat(result.info.groupParameters).isEqualTo(deserializedParams)
        assertThat(result.info.mgmPublicSigningKey).isEqualTo(ByteBuffer.wrap(serializedPubKey))
        assertThat(result.info.mgmPrivateSigningKey).isEqualTo(ByteBuffer.wrap(serializedPrivateKey))
    }

    @Test
    fun `Null is returned if no results found in the DB`() {
        whenever(entityManager.find(eq(StaticNetworkInfoEntity::class.java), eq(groupId))).doReturn(null)

        val result = handler.invoke(mock(), QueryStaticNetworkInfo("groupId"))

        assertThat(result.info).isNull()
    }
}

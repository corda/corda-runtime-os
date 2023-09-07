package net.corda.libs.statemanager.impl

import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

class StateManagerImplTest {
    private val entityManager = mock<EntityManager>().apply {
        whenever(transaction).thenReturn(mock())
    }
    private val entityManagerFactory = mock<EntityManagerFactory>().apply {
        whenever(createEntityManager()).thenReturn(entityManager)
    }
    private val stateRepository: StateRepository = mock()
    private val serializerFactory: CordaAvroSerializationFactory = mock()

    private val cordaAvroDeserializer = mock<CordaAvroDeserializer<Any>>()

    private val now = Instant.now()
    private val stateDtoA = mock<StateEntity>().apply {
        whenever(value).thenReturn("a".toByteArray())
        whenever(key).thenReturn("a")
        whenever(version).thenReturn(1)
        whenever(modifiedTime).thenReturn(now)
        whenever(metadata).thenReturn("{\"metadatakey\": \"metadatavalue\"}")
    }

    private val stateManager = StateManagerImpl(
        stateRepository = stateRepository,
        entityManagerFactory = entityManagerFactory,
    )

    @Test
    fun `get state creates tx and uses repository`() {
        val key = "a"
        val keys = setOf("a")
        val stateDtos = listOf(stateDtoA)

        whenever(serializerFactory.createAvroDeserializer(any(), eq(Any::class.java))).thenReturn(cordaAvroDeserializer)
        whenever(entityManagerFactory.createEntityManager()).thenReturn(entityManager)
        whenever(stateRepository.get(eq(entityManager), eq(keys))).thenReturn(stateDtos)
        whenever(cordaAvroDeserializer.deserialize(any())).thenReturn(Any())

        val result = stateManager.get(keys)

        assertThat(result.keys).isEqualTo(keys)
        val metadata = result[key]!!.metadata
        assertThat(metadata["metadatakey"]).isNotNull()
        assertThat(metadata["metadatakey"]).isEqualTo("metadatavalue")
    }
}

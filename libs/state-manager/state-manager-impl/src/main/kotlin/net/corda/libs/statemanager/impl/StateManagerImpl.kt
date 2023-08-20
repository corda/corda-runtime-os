package net.corda.libs.statemanager.impl

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import net.corda.orm.utils.transaction
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.persistence.EntityManagerFactory

@Suppress("ForbiddenComment")
class StateManagerImpl(
    private val stateRepository: StateRepository,
    private val entityManagerFactory: EntityManagerFactory,
    private val serializerFactory: CordaAvroSerializationFactory,
    private val serializers: MutableMap<Class<*>, CordaAvroSerializer<*>> = ConcurrentHashMap(),
    private val deserializers: MutableMap<Class<*>, CordaAvroDeserializer<*>> = ConcurrentHashMap(),
) : StateManager {

    private val objectMapper = ObjectMapper()

    @Suppress("UNCHECKED_CAST")
    private fun <S : Any> getOrCreateDeserializer(clazz: Class<S>): CordaAvroDeserializer<S> {
        return deserializers.computeIfAbsent(clazz) {
            serializerFactory.createAvroDeserializer({ _ -> }, clazz)
        } as CordaAvroDeserializer<S>
    }

    @Suppress("UNCHECKED_CAST")
    private fun <S : Any> getOrCreateSerializer(clazz: Class<S>): CordaAvroSerializer<S> {
        return serializers.computeIfAbsent(clazz) {
            serializerFactory.createAvroSerializer<S>()
        } as CordaAvroSerializer<S>
    }

    private fun <S : Any> StateEntity.toState(deserializer: CordaAvroDeserializer<S>) =
        State(
            deserializer.deserialize(state)!!,
            key,
            metadata.toMetadataMap(),
            version,
            modifiedTime,
        )

    private fun String.toMetadataMap() =
        objectMapper.readValue(this, object : TypeReference<Metadata<Any>>() {})

    private fun <R : Any> State<R>.toDto(serializer: CordaAvroSerializer<R>): StateEntity {
        return StateEntity(
            key,
            serializer.serialize(state)!!,
            objectMapper.writeValueAsString(metadata),
            version,
            modifiedTime
        )
    }

    override fun <S : Any> create(clazz: Class<S>, states: Set<State<S>>) {
        val dtoInstances = states.map { it.toDto(getOrCreateSerializer(clazz)) }
        entityManagerFactory.transaction { em ->
            stateRepository.create(em, dtoInstances)
        }
    }

    override fun <S : Any> get(clazz: Class<S>, keys: Set<String>): Map<String, State<S>> {
        val deserializer = getOrCreateDeserializer(clazz)

        return entityManagerFactory.transaction { em ->
            stateRepository.get(em, keys)
        }
            .map { it.toState(deserializer) }
            .associateBy { it.key }
    }

    override fun <S : Any> update(clazz: Class<S>, states: Set<State<S>>): Map<String, State<S>> {
        // TODO: return states that failed the optimistic locking check
        val mismatchVersions = mutableMapOf<String, State<S>>()
        val dtoInstances = states.map { it.toDto(getOrCreateSerializer(clazz)) }

        entityManagerFactory.transaction { em ->
            stateRepository.update(em, dtoInstances)
        }

        return mismatchVersions
    }

    override fun <S : Any> delete(clazz: Class<S>, keys: Set<String>): Map<String, State<S>> {
        // TODO: return states that failed the optimistic locking check
        val mismatchVersions = mutableMapOf<String, State<S>>()

        entityManagerFactory.transaction { em ->
            stateRepository.delete(em, keys)
        }

        return mismatchVersions
    }

    override fun <S : Any> getUpdatedBetween(clazz: Class<S>, start: Instant, finish: Instant): Map<String, State<S>> {
        val deserializer = getOrCreateDeserializer(clazz)

        return entityManagerFactory.transaction { em ->
            stateRepository.findUpdatedBetween(em, start, finish)
        }
            .map { it.toState(deserializer) }
            .associateBy { it.key }
    }

    override fun close() {
        entityManagerFactory.close()
    }
}

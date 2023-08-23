package net.corda.libs.statemanager.impl

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.persistence.EntityManagerFactory
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.statemanager.State
import net.corda.libs.statemanager.StateManager
import net.corda.libs.statemanager.impl.dto.StateDto
import net.corda.libs.statemanager.impl.repository.StateManagerRepository
import net.corda.orm.utils.transaction

class StateManagerImpl(
    private val stateManagerRepository: StateManagerRepository,
    private val entityManagerFactory: EntityManagerFactory,
    private val serializerFactory: CordaAvroSerializationFactory
) : StateManager {

    private val objectMapper = object : ThreadLocal<ObjectMapper>() {
        override fun initialValue() = ObjectMapper()
    }

    private val serializers: MutableMap<Class<*>, CordaAvroSerializer<*>> = ConcurrentHashMap()
    private val deserializers: MutableMap<Class<*>, CordaAvroDeserializer<*>> = ConcurrentHashMap()

    override fun <S : Any> get(clazz: Class<S>, keys: Set<String>): Map<String, State<S>> {
        val deserializer = getOrCreateDeserializer(clazz)
        return entityManagerFactory.transaction { em ->
            stateManagerRepository.get(em, keys)
        }
            .map { it.toState(deserializer) }
            .associateBy { it.key }
    }

    override fun <S : Any> put(clazz: Class<S>, states: Set<State<S>>): Map<String, State<S>> {
        val deserializer = getOrCreateDeserializer(clazz)
        val dtos = states.map { it.toDto(getOrCreateSerializer(clazz)) }
        return entityManagerFactory.transaction { em ->
            stateManagerRepository.put(em, dtos)
        }
            .map { it.toState(deserializer) }
            .associateBy { it.key }
    }

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

    private fun <S : Any> StateDto.toState(deserializer: CordaAvroDeserializer<S>) = State(
        state?.let { deserializer.deserialize(state) },
        key,
        version,
        modifiedTime.toEpochMilli(),
        metadata?.unmarshallJsonToMap()
    )

    private fun String.unmarshallJsonToMap() =
        objectMapper.get().readValue(this, object : TypeReference<Map<String, String>>() {}).toMutableMap()

    private fun <R : Any> State<R>.toDto(serializer: CordaAvroSerializer<R>): StateDto {
        val stateOrNull = state?.let { serializer.serialize(state!!) }
        return StateDto(
            key,
            stateOrNull,
            version,
            objectMapper.get().writeValueAsString(metadata),
            Instant.ofEpochMilli(modifiedTime)
        )
    }

    override fun close() {
        entityManagerFactory.close()
    }
}
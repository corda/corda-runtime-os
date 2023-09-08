package net.corda.libs.statemanager.impl

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import net.corda.orm.utils.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.persistence.EntityManagerFactory

@Suppress("ForbiddenComment")
class StateManagerImpl(
    private val stateRepository: StateRepository,
    private val entityManagerFactory: EntityManagerFactory,
) : StateManager {

    private val objectMapper = ObjectMapper()

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private fun State.toPersistentEntity(): StateEntity =
        StateEntity(key, value, objectMapper.writeValueAsString(metadata), version, modifiedTime)

    private fun StateEntity.fromPersistentEntity() =
        State(key, value, version, metadata.toMetadataMap(), modifiedTime,)

    private fun String.toMetadataMap() =
        objectMapper.readValue(this, object : TypeReference<Metadata<Any>>() {})

    override fun create(states: Collection<State>): Map<String, Exception> {
        val failures = mutableMapOf<String, Exception>()

        states.map {
            it.toPersistentEntity()
        }.forEach {
            try {
                entityManagerFactory.transaction { em ->
                    stateRepository.create(em, it)
                }
            } catch (e: Exception) {
                logger.warn("Failed to create state with id ${it.key}", e)
                failures[it.key] = e
            }
        }

        return failures
    }

    override fun get(keys: Collection<String>): Map<String, State> {
        return entityManagerFactory.transaction { em ->
            stateRepository.get(em, keys)
        }.map {
            it.fromPersistentEntity()
        }.associateBy {
            it.key
        }
    }

    override fun update(states: Collection<State>): Map<String, State> {
        // TODO: return states that failed the optimistic locking check
        val mismatchVersions = mutableMapOf<String, State>()

        entityManagerFactory.transaction { em ->
            stateRepository.update(em, states.map { it.toPersistentEntity() })
        }

        return mismatchVersions
    }

    override fun delete(keys: Collection<String>): Map<String, State> {
        // TODO: return states that failed the optimistic locking check
        val mismatchVersions = mutableMapOf<String, State>()

        entityManagerFactory.transaction { em ->
            stateRepository.delete(em, keys)
        }

        return mismatchVersions
    }

    override fun getUpdatedBetween(start: Instant, finish: Instant): Map<String, State> {

        return entityManagerFactory.transaction { em ->
            stateRepository.findUpdatedBetween(em, start, finish)
        }
            .map { it.fromPersistentEntity() }
            .associateBy { it.key }
    }

    override fun close() {
        entityManagerFactory.close()
    }
}

package net.corda.libs.statemanager.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.utils.transaction
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateOperationGroupBuilder
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import org.slf4j.LoggerFactory

class StateOperationGroupBuilderImpl(
    private val dataSource: CloseableDataSource,
    private val repository: StateRepository,
    private val objectMapper: ObjectMapper
) : StateOperationGroupBuilder {

    private val stateKeys = mutableSetOf<String>()
    private val creates = mutableListOf<State>()
    private val updates = mutableListOf<State>()
    private val deletes = mutableListOf<State>()

    override fun create(states: Collection<State>): StateOperationGroupBuilder {
        addToList(states, creates)
        return this
    }

    override fun update(states: Collection<State>): StateOperationGroupBuilder {
        addToList(states, updates)
        return this
    }

    override fun delete(states: Collection<State>): StateOperationGroupBuilder {
        addToList(states, deletes)
        return this
    }

    private fun addToList(states: Collection<State>, list: MutableList<State>) {
        states.forEach { state ->
            if (state.key in stateKeys) {
                throw IllegalArgumentException(
                    "Attempted to add state with key ${state.key} more than once to the same update batch"
                )
            }
            stateKeys.add(state.key)
            list.add(state)
        }
    }

    override fun execute(): Map<String, State?> {
        return dataSource.connection.transaction { connection ->
            val createFailures = repository.create(
                connection,
                creates.map { state -> state.toPersistentEntity() }
            ).let { successes ->
                (creates.map { it.key }.toSet() - successes.toSet()).associateWith { null }
            }

            val updateFailures = repository.update(
                connection,
                updates.map { state -> state.toPersistentEntity() }
            ).let { (_, failed) ->
                val failedStates = repository.get(connection, failed)
                    .map { it.fromPersistentEntity() }
                    .associateBy { it.key }
                failedStates + (failed - failedStates.keys).associateWith { null }
            }

            val deleteFailures = repository.delete(
                connection,
                deletes.map { state -> state.toPersistentEntity() }
            ).let { failures ->
                repository.get(connection, failures)
                    .map { it.fromPersistentEntity() }
                    .associateBy { it.key }
            }

            createFailures + updateFailures + deleteFailures
        }
    }

    private fun State.toPersistentEntity(): StateEntity =
        StateEntity(key, value, objectMapper.writeValueAsString(metadata), version, modifiedTime)

    private fun StateEntity.fromPersistentEntity() =
        State(key, value, version, objectMapper.convertToMetadata(metadata), modifiedTime)
}
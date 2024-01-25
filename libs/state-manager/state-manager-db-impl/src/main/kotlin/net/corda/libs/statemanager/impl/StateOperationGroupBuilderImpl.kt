package net.corda.libs.statemanager.impl

import net.corda.db.core.CloseableDataSource
import net.corda.db.core.utils.transaction
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateOperationGroupBuilder
import net.corda.libs.statemanager.impl.repository.StateRepository

class StateOperationGroupBuilderImpl(
    private val dataSource: CloseableDataSource,
    private val repository: StateRepository
) : StateOperationGroupBuilder {

    private val stateKeys = mutableSetOf<String>()
    private val creates = mutableListOf<State>()
    private val updates = mutableListOf<State>()
    private val deletes = mutableListOf<State>()
    private var executed = false

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
        if (executed) {
            throw IllegalStateException("Attempted to add states to already executed state operation batch")
        }
        states.forEach { state ->
            if (state.key in stateKeys) {
                throw IllegalArgumentException(
                    "Attempted to add state with key ${state.key} more than once to the same operation batch"
                )
            }
            stateKeys.add(state.key)
            list.add(state)
        }
    }

    override fun execute(): Map<String, State?> {
        if (executed) {
            throw IllegalStateException("Attempted to execute a batch that has already been executed")
        }
        return dataSource.connection.transaction { connection ->
            val createFailures = repository.create(
                connection,
                creates
            ).let { successes ->
                (creates.map { it.key }.toSet() - successes.toSet()).toList()
            }

            val updateFailures = repository.update(
                connection,
                updates
            ).failedKeys

            val deleteFailures = repository.delete(
                connection,
                deletes
            )

            val failedKeys = createFailures + updateFailures + deleteFailures
            val failedStates = repository.get(connection, failedKeys).associateBy { it.key }
            val nonExistentStateFailures = (createFailures + updateFailures).filter {
                it !in failedStates.keys
            }.associateWith { null }

            failedStates + nonExistentStateFailures
        }.also {
            executed = true
        }
    }
}
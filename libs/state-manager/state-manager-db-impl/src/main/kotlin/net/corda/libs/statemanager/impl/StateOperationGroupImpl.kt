package net.corda.libs.statemanager.impl

import net.corda.db.core.CloseableDataSource
import net.corda.db.core.utils.transaction
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateOperationGroup
import net.corda.libs.statemanager.impl.repository.StateRepository

class StateOperationGroupImpl(
    private val dataSource: CloseableDataSource,
    private val repository: StateRepository
) : StateOperationGroup {

    private val stateKeys = mutableSetOf<String>()
    private val creates = mutableListOf<State>()
    private val updates = mutableListOf<State>()
    private val deletes = mutableListOf<State>()
    private var executed = false

    override fun create(states: Collection<State>): StateOperationGroup {
        addToList(states, creates)
        return this
    }

    override fun update(states: Collection<State>): StateOperationGroup {
        addToList(states, updates)
        return this
    }

    override fun delete(states: Collection<State>): StateOperationGroup {
        addToList(states, deletes)
        return this
    }

    private fun addToList(states: Collection<State>, list: MutableList<State>) {
        if (executed) {
            throw IllegalStateException("Attempted to add states to already executed state operation group")
        }
        states.forEach { state ->
            if (state.key in stateKeys) {
                throw IllegalArgumentException(
                    "Attempted to add state with key ${state.key} more than once to the same operation group"
                )
            }
            stateKeys.add(state.key)
            list.add(state)
        }
    }

    override fun execute(): Map<String, State?> {
        if (executed) {
            throw IllegalStateException("Attempted to execute a group that has already been executed")
        }
        return dataSource.connection.transaction { connection ->
            println("AAA in transaction $connection")
            println("AAA \t creates - ${creates.size}")
            val createFailures = repository.create(
                connection,
                creates
            ).let { successes ->
                (creates.map { it.key }.toSet() - successes.toSet()).toList()
            }

            println("AAA \t updates - ${updates.size}")
            val updateFailures = repository.update(
                connection,
                updates
            ).failedKeys
            println("AAA \t updateFailures - $updateFailures")

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

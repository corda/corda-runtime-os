package net.corda.messaging.mediator.slim

import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

class SlimStateService(
    private val stateManager: StateManager,
) {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val getStateDispatch = AsyncBatchProcessor(::handleGetBatch, 500)
    private val createStateDispatch = AsyncBatchProcessor(::handleCreateBatch, 500)
    private val updateStateDispatch = AsyncBatchProcessor(::handleUpdateBatch, 500)
    private val deleteStateDispatch = AsyncBatchProcessor(::handleDeleteBatch, 500)

    fun getState(key: String): CompletableFuture<State?> {
        return getStateDispatch.enqueueRequest(key)
    }

    fun createState(state: State): CompletableFuture<Unit> {
        return createStateDispatch.enqueueRequest(state)
    }

    fun updateState(state: State): CompletableFuture<Unit> {
        return updateStateDispatch.enqueueRequest(state)
    }

    fun deleteState(state: State): CompletableFuture<Unit> {
        return deleteStateDispatch.enqueueRequest(state)
    }

    private fun handleGetBatch(items: Collection<AsyncBatchProcessor.RequestItem<String, State?>>) {
        try {
            val states = stateManager.get(items.map { it.request })
            items.forEach { request ->
                request.requestFuture.complete(states[request.request])
            }
        } catch (e: Exception) {
            logger.error("Failed to retrieve state", e)
            items.forEach { it.requestFuture.completeExceptionally(e) }
        }
    }

    private fun handleCreateBatch(items: Collection<AsyncBatchProcessor.RequestItem<State, Unit>>) {
        applyStateAction(items) { stateManager.create(it) }
    }

    private fun handleUpdateBatch(items: Collection<AsyncBatchProcessor.RequestItem<State, Unit>>) {
        applyStateAction(items) { stateManager.update(it) }
    }

    private fun handleDeleteBatch(items: Collection<AsyncBatchProcessor.RequestItem<State, Unit>>) {
        applyStateAction(items) { stateManager.delete(it) }
    }

    private fun applyStateAction(
        items: Collection<AsyncBatchProcessor.RequestItem<State, Unit>>,
        action: (Collection<State>) -> Unit,
    ) {
        try {
            action(items.map { it.request })
            items.accept()
        } catch (e: Exception) {
            items.fail(e)
        }
    }

    private fun Collection<AsyncBatchProcessor.RequestItem<State, Unit>>.accept() {
        this.forEach { it.requestFuture.complete(Unit) }
    }

    private fun Collection<AsyncBatchProcessor.RequestItem<State, Unit>>.fail(e: Exception) {
        this.forEach { it.requestFuture.completeExceptionally(e) }
    }
}

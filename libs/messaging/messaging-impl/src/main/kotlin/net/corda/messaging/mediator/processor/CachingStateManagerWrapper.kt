package net.corda.messaging.mediator.processor

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import java.time.Duration

class CachingStateManagerWrapper(val stateManager: StateManager) {

    private val cache: Cache<String, State?> = CacheFactoryImpl().buildNonAsync(
        stateManager.name.toString(),
        Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofSeconds(600))
    )

    private fun cacheState(state: State) {
        cache.asMap().compute(state.key) { _, value ->
            if (value == null) {
                state
            } else if (value.version > state.version) {
                value
            } else state
        }
    }

    fun create(states: Collection<State>): Set<String> {
        return stateManager.create(states).apply {
            states.forEach { state ->
                if (state.key !in this) {
                    cacheState(state)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun get(keys: Collection<String>, forceGetKeys: Collection<String> = emptySet()): Map<String, State> {
        val keySet = keys.toSet()
        val forceGetKeySet = forceGetKeys.toSet()
        val keysToSearchInCache = keySet - forceGetKeySet
        val foundInCache = cache.getAllPresent(keysToSearchInCache) as Map<String, State>
        val keysToSearchInSource = (keySet - foundInCache.keys) + forceGetKeySet
        val foundInSource = stateManager.get(keysToSearchInSource).apply {
            this.forEach { cacheState(it.value) }
        }
        return foundInCache + foundInSource
    }

    fun update(states: Collection<State>): Map<String, State?> {
        return stateManager.update(states).apply {
            states.forEach { state ->
                if (state.key in this) {
                    cacheState(this[state.key]!!)
                } else {
                    cacheState(state.copy(version = state.version + 1))
                }
            }
        }
    }

    fun delete(states: Collection<State>): Map<String, State> {
        return stateManager.delete(states).apply {
            this.forEach { cacheState(it.value) }
        }
    }

    fun invalidate() {
        cache.invalidateAll()
    }
}
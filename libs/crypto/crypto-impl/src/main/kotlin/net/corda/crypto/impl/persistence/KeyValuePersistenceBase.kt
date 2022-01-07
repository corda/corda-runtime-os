package net.corda.crypto.impl.persistence

/**
 * Base class which can be used to simplify the mutations.
 */
abstract class KeyValuePersistenceBase<V: IHaveTenantId, E: IHaveTenantId>(
    private val mutator: KeyValueMutator<V, E>
) : KeyValuePersistence<V, E> {
    protected fun mutate(entity: E?): V? {
        return if(entity != null) {
            mutator.mutate(entity)
        } else {
            null
        }
    }
}
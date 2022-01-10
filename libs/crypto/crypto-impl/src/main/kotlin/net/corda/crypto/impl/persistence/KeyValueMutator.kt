package net.corda.crypto.impl.persistence

fun interface KeyValueMutator<V, E> {
    fun  mutate(entity: E): V
}

fun <V, E> KeyValueMutator<V, E>.mutateOrNull(entity: E?): V? {
    return if(entity != null) {
        mutate(entity)
    } else {
        null
    }
}
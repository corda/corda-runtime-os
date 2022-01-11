package net.corda.crypto.impl.persistence

fun interface KeyValueMutator<V, E> {
    fun  mutate(entity: E): V
}

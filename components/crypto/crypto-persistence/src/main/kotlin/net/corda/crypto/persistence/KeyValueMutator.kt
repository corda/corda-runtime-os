package net.corda.crypto.persistence

fun interface KeyValueMutator<V, E> {
    fun  mutate(entity: E): V
}

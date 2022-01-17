package net.corda.crypto.component.persistence

fun interface KeyValueMutator<V, E> {
    fun  mutate(entity: E): V
}

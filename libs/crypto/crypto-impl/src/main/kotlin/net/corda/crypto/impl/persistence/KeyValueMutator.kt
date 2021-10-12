package net.corda.crypto.impl.persistence

fun interface KeyValueMutator<V: Any, E: Any> {
    fun  mutate(entity: E): V
}
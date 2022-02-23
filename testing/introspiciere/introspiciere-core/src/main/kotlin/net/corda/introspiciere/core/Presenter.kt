package net.corda.introspiciere.core

fun interface Presenter<T> {
    fun present(output: T)
}

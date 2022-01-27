package net.corda.introspiciere.core

interface Presenter<T> {
    fun present(output: T)
}
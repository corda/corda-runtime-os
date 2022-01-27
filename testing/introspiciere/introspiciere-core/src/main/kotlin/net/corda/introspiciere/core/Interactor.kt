package net.corda.introspiciere.core

interface Interactor<T> {
    fun execute(input: T)
}
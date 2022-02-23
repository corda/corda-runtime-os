package net.corda.introspiciere.core

/**
 * Defines a use case in the core. It will eventually replace the [Interactor] interface.
 */
interface UseCase<INPUT> {
    fun execute(input: INPUT)
}
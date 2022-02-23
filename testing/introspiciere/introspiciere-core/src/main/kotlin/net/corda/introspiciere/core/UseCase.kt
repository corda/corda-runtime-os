package net.corda.introspiciere.core

/**
 * Defines a use case in the core.
 */
interface UseCase<INPUT> {
    fun execute(input: INPUT)
}
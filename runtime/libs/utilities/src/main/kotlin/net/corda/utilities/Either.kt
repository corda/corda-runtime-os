package net.corda.utilities

sealed interface Either<out T, out S> {
    data class Left<T>(
        val a: T
    ) : Either<T, Nothing>
    data class Right<S>(
        val b: S
    ) : Either<Nothing, S>
}

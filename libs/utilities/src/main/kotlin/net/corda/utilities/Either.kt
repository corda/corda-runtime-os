package net.corda.utilities

sealed interface Either<out T, out S> {
    data class Left<T>(
        val a: T,
    ) : Either<T, Nothing>
    data class Right<S>(
        val b: S,
    ) : Either<Nothing, S>

    fun <U> mapRight(mapper: (S) -> U): Either<T, U> {
        return when (this) {
            is Left -> Left(this.a)
            is Right -> Right(mapper(this.b))
        }
    }

    fun asLeft(): T? {
        return when (this) {
            is Left -> this.a
            is Right -> null
        }
    }

    fun asRight(): S? {
        return when (this) {
            is Right -> this.b
            is Left -> null
        }
    }
}

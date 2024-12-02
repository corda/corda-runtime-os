package net.corda.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EitherTest {
    @Test
    @Suppress("UNREACHABLE_CODE")
    fun `mapRight will leave the left value as is`() {
        val either = Either.Left(200)

        assertThat(either.mapRight { "$it" }).isEqualTo(either)
    }

    @Test
    fun `mapRight will map the right value`() {
        val either = Either.Right(200)

        assertThat(either.mapRight { "$it" }).isEqualTo(Either.Right("200"))
    }

    @Test
    fun `asRight will return the right value`() {
        val either = Either.Right(200)

        assertThat(either.asRight()).isEqualTo(200)
    }
    @Test
    fun `asRight will return the null for left value`() {
        val either: Either<Int, String> = Either.Left(200)

        assertThat(either.asRight()).isNull()
    }

    @Test
    fun `asLeft will return the left value`() {
        val either = Either.Left(200)

        assertThat(either.asLeft()).isEqualTo(200)
    }
    @Test
    fun `asLeft will return the null for right value`() {
        val either: Either<Int, String> = Either.Right("200")

        assertThat(either.asLeft()).isNull()
    }
}
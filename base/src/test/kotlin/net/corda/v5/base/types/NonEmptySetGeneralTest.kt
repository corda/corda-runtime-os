package net.corda.v5.base.types

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class NonEmptySetGeneralTest {
    @Test
    fun `copyOf - empty source`() {
        Assertions.assertThatThrownBy { NonEmptySet.copyOf(HashSet<Int>()) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun head() {
        Assertions.assertThat(NonEmptySet.of(1, 2).head()).isEqualTo(1)
    }
}
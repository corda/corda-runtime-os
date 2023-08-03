package net.corda.e2etest.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MapUtilsTest {

    @Nested
    inner class FlattenMap {

        @Test
        fun `non-nested map is unchanged`() {
            val input = mapOf(
                "foo" to "bar",
                "foo.again" to "bar"
            )

            assertThat(input.flatten()).isEqualTo(input)
        }

        @Test
        fun `nested map is flattened`() {
            val input = mapOf(
                "foo" to mapOf("again" to "bar")
            )

            val expected = mapOf(
                "foo.again" to "bar"
            )

            assertThat(input.flatten()).isEqualTo(expected)
        }

        @Test
        fun `mix dot notation and nested map is flatten as expected`() {
            val input = mapOf(
                "foo.first" to "bar1",
                "foo" to mapOf("second" to "bar2")
            )

            val expected = mapOf(
                "foo.first" to "bar1",
                "foo.second" to "bar2"
            )

            assertThat(input.flatten()).isEqualTo(expected)
        }

        @Test
        fun `multi layered nesting is flattened as expected`() {
            val input = mapOf(
                "foo" to mapOf(
                    "bar" to mapOf(
                        "baz" to mapOf(
                            "qux" to mapOf(
                                "quux" to "test"
                            )
                        )
                    )
                ),
            )

            val expected = mapOf(
                "foo.bar.baz.qux.quux" to "test"
            )

            assertThat(input.flatten()).isEqualTo(expected)
        }

        @Test
        fun `multi layered nesting is flattened as expected with different data types`() {
            val input = mapOf(
                "foo" to mapOf(
                    0 to mapOf(
                        true to mapOf(
                            1L to mapOf(
                                'c' to 3.14
                            )
                        )
                    )
                ),
            )

            val expected = mapOf(
                "foo.0.true.1.c" to 3.14
            )

            assertThat(input.flatten()).isEqualTo(expected)
        }

        @Test
        fun `nested map is flattened into target map`() {
            val target = mutableMapOf<String, Any?>()
            val input = mapOf(
                "foo" to mapOf("again" to "bar")
            )

            val expected = mapOf(
                "foo.again" to "bar"
            )
            input.flatten(target)
            assertThat(target).isEqualTo(expected)
        }

        @Test
        fun `prefix is applied to flattened properties if specified`() {
            val input = mapOf(
                "foo" to mapOf("again" to "bar")
            )

            val expected = mapOf(
                "mytest.foo.again" to "bar"
            )
            assertThat(input.flatten(prefix = "mytest")).isEqualTo(expected)
        }

        @Test
        fun `map value can be null`() {
            val input = mapOf(
                "foo" to mapOf("again" to null)
            )

            val expected = mapOf(
                "foo.again" to null
            )
            assertThat(input.flatten()).isEqualTo(expected)
        }

        @Test
        fun `large map with mix of input is flattened as expected`() {
            val input = mapOf(
                "one" to 1,
                "two" to false,
                "three" to 'a',
                "four" to null,
                "five" to mapOf(
                    "six.first" to 1,
                    "six.second.one" to "test1",
                    "six" to mapOf(
                        "second.two" to "test2"
                    )
                ),
                "five.six.third" to false
            )

            val expected = mapOf(
                "test.one" to 1,
                "test.two" to false,
                "test.three" to 'a',
                "test.four" to null,
                "test.five.six.first" to 1,
                "test.five.six.second.one" to "test1",
                "test.five.six.second.two" to "test2",
                "test.five.six.third" to false
            )
            val target = mutableMapOf<String, Any?>()
            input.flatten(target, prefix="test")
            assertThat(target).isEqualTo(expected)
        }

        @Test
        fun `flatten empty map`() {
            val input = emptyMap<String, Any?>()

            assertThat(input.flatten()).isEqualTo(input)
        }
    }

    @Nested
    inner class ExpandTest {

        @Test
        fun `test expand() with nested keys`() {
            val input = mapOf(
                "a.b.c" to 1,
                "a.b.d" to 2,
                "x.y.z" to "hello"
            )

            val result = input.expand()

            val expected = mapOf(
                "a" to mapOf(
                    "b" to mapOf(
                        "c" to 1,
                        "d" to 2
                    )
                ),
                "x" to mapOf(
                    "y" to mapOf(
                        "z" to "hello"
                    )
                )
            )
            assertThat(result).isEqualTo(expected)
        }

        @Test
        fun `test expand() with non-nested keys`() {
            val input = mapOf(
                "a" to 1,
                "b" to 2,
                "c" to "hello"
            )

            val result = input.expand()

            val expected = mapOf(
                "a" to 1,
                "b" to 2,
                "c" to "hello"
            )
            assertThat(result).isEqualTo(expected)
        }

        @Test
        fun `test expand() with empty input map`() {
            val input = emptyMap<String, Any?>()

            val result = input.expand()

            val expected = emptyMap<String, Any?>()
            assertThat(result).isEqualTo(expected)
        }

        @Test
        fun `test expand() with null values`() {
            val input = mapOf(
                "a.b" to null,
                "x.y.z" to null
            )

            val result = input.expand()

            val expected = mapOf(
                "a" to mapOf("b" to null),
                "x" to mapOf("y" to mapOf("z" to null))
            )
            assertThat(result).isEqualTo(expected)
        }
    }
}
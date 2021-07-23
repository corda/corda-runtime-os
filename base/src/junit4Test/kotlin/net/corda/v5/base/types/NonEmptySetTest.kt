package net.corda.v5.base.types

import com.google.common.collect.testing.SetTestSuiteBuilder
import com.google.common.collect.testing.TestIntegerSetGenerator
import com.google.common.collect.testing.features.CollectionFeature
import com.google.common.collect.testing.features.CollectionSize
import junit.framework.TestSuite
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    NonEmptySetTest.Guava::class,
    NonEmptySetTest.General::class
)
class NonEmptySetTest {
    object Guava {
        @JvmStatic
        fun suite(): TestSuite {
            return SetTestSuiteBuilder
                .using(NonEmptySetGenerator)
                .named("Guava test suite")
                .withFeatures(
                    CollectionSize.SEVERAL,
                    CollectionFeature.ALLOWS_NULL_VALUES,
                    CollectionFeature.KNOWN_ORDER
                )
                .createTestSuite()
        }
    }

    class General {
        @Test(timeout = 300_000)
        fun `copyOf - empty source`() {
            assertThatThrownBy { NonEmptySet.copyOf(HashSet<Int>()) }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test(timeout = 300_000)
        fun head() {
            assertThat(NonEmptySet.of(1, 2).head()).isEqualTo(1)
        }
    }

    private object NonEmptySetGenerator : TestIntegerSetGenerator() {
        override fun create(elements: Array<out Int?>): NonEmptySet<Int?> = NonEmptySet.copyOf(elements.asList())
    }
}

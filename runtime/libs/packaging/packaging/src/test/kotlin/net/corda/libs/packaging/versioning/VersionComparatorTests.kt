package net.corda.libs.packaging.versioning

import net.corda.libs.packaging.core.comparator.VersionComparator.Companion.cmp
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.stream.Stream

class VersionComparatorTests {
    private class EdgeCaseProvider : ArgumentsProvider {
        // Generated tests from fuzzer threw up some odd things that ought to be
        // defensively coded against.
        // No one should ever pass in this kind of stuff but you never know.
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> {
            return Stream.of(
                Arguments.of("", "", 0),
                Arguments.of("5", "l", 1),
                Arguments.of(":", "r", 1),
                Arguments.of("h", "!", -1),
                Arguments.of("!", "8", -1),

                // Leading non-alphanumeric junk that returns non-zero if different (sub)string length
                Arguments.of("aaa:@@@@1.0", "aaa:@@1.0", 1),
                Arguments.of("aaa:$$$$1.0", "aaa:$$1.0", 1),

                // For whatever reason, you can have differing leading non-alphanumeric junk that returns zero if it's the same (sub)string length
                Arguments.of("$$$$1.0", "££££1.0", 0),
                Arguments.of("aaa:$$$$1.0", "aaa:$$$$1.0", 0),

                Arguments.of("z:74", "zO:4", -1),
                Arguments.of("W:25991.6882", "WFeb:48791", -1),
                Arguments.of("02.67174.1", "2.3447.96465", 1),
                Arguments.of("7-eHQwmPfK", "74-CCSnIoQ", -1)
            )
        }
    }

    @ParameterizedTest(name = "version1: \"{0}\", version2: \"{1}\", expected outcome: {2}")
    @ArgumentsSource(
        EdgeCaseProvider::class
    )
    fun `test version edge cases`(version1: String?, version2: String?, expectedOutcome: Int) {
        Assertions.assertEquals(expectedOutcome, cmp(version1, version2), "when checking with \"$version1\", \"$version2\"")
        Assertions.assertEquals(oppositeOutcome(expectedOutcome), cmp(version2, version1), "when checking with \"$version1\", \"$version2\"")
    }

    private class TestCaseProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> {
            return Stream.of(
                Arguments.of("asdfg-2019", "asdfg-2019", 0),
                Arguments.of("asdfg-2019", "asdfg-2020", -1),
                Arguments.of("", "asdfg-2019", -1),
                Arguments.of("", "1.5", -1),
                Arguments.of("", null, 1),
                Arguments.of("1.2", "1", 1),
                Arguments.of("1.09", "1.09", 0),
                Arguments.of("1.2", "1.0", 1),
                Arguments.of("1.00", "1.0", 0),
                Arguments.of("1.2", "1.09", -1),
                Arguments.of("1.2", "1.10", -1),
                Arguments.of("1.2", "1.100", -1),
                Arguments.of("5.9.12.arch1-1", "5.8.0.arch1-1", 1),
                Arguments.of("5.9.12.arch1-1", "5.10.0.arch1-1", -1),
                Arguments.of("5.10.0.arch1-1", "5.10.0.arch1-3", -1),
                Arguments.of("5.10.0.arch1-1", "5.10.0.arch1-9", -1),
                Arguments.of("5.10.0.arch1-10", "5.10.0.arch1-3", 1),
                Arguments.of("5.9.0.arch1-10", "5.10.0.arch1-3", -1),
                Arguments.of("20191220.6871bff-1", "20201120.bc9cd0b-1", -1),

                Arguments.of("123", "124", -1),
                Arguments.of(":", "123", -1),
                Arguments.of("123:", "124:", -1),
                Arguments.of("123a", "123b", -1)
            )
        }
    }

    @ParameterizedTest(name = "version1: \"{0}\", version2: \"{1}\", expected outcome: {2}")
    @ArgumentsSource(
        TestCaseProvider::class
    )
    fun `test version`(version1: String?, version2: String?, expectedOutcome: Int) {
        Assertions.assertEquals(expectedOutcome, cmp(version1, version2))
        Assertions.assertEquals(oppositeOutcome(expectedOutcome), cmp(version2, version1))
    }

    private class EpochTestCaseProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> {
            return Stream.of(
                // "epoch" tests, even though we don't use them, the code paths are there.
                Arguments.of("123:1.2.3", "456:1.2.4", -1),
                Arguments.of("bbb:1.2.3", "bbb:1.2.4", -1),
                Arguments.of("bbb:1.2.3", "bbb:1.2.3", 0),
                Arguments.of("bbb:1.2.3", "bbb:1.2.2", 1),
                Arguments.of("bbb:1.2.3", "ccc:1.2.3", -1),
                Arguments.of("bbb:1.2.3", "aaa:1.2.2", 1),
                // "version" = "abc:def" NOT "epoch" = "abc" and version = "def"
                Arguments.of("abc:def-ghi", "abc:def-ghij", -1),
            )
        }
    }

    @ParameterizedTest(name = "version1: \"{0}\", version2: \"{1}\", expected outcome: {2}")
    @ArgumentsSource(
        EpochTestCaseProvider::class
    )
    fun `test version with epochs`(version1: String?, version2: String?, expectedOutcome: Int) {
        Assertions.assertEquals(expectedOutcome, cmp(version1, version2), "when checking with \"$version1\", \"$version2\"")
        Assertions.assertEquals(oppositeOutcome(expectedOutcome), cmp(version2, version1), "when checking with \"$version1\", \"$version2\"")
    }

    private class ReleaseTestCaseProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> {
            return Stream.of(
                Arguments.of("123:1.2.3.4.5", "123:1.2.3.4.6", -1),
                Arguments.of("123:1.2.3-abc", "123:1.2.3-abc", 0),
                Arguments.of("123:1.2.3-abc", "123:1.3.4", -1),

                // These are really *stupid*, but it's what the old code did.
                // Do we REALLY want this?
                Arguments.of("123:1.2.3-SNAPSHOT", "123:1.2.3", 0),
                Arguments.of("123:1.2.3-20210908", "123:1.2.3", 0),

                // and more
                Arguments.of("1.2.3", "1.2.3-release", 0),
                Arguments.of(":", "-release", 0),
                Arguments.of(":-", "-release", 1),
                Arguments.of("0:", "-release", 0),
                Arguments.of("0", "-release", 1),
            )
        }
    }

    @ParameterizedTest(name = "version1: \"{0}\", version2: \"{1}\", expected outcome: {2}")
    @ArgumentsSource(
        ReleaseTestCaseProvider::class
    )
    fun `test version with release`(version1: String?, version2: String?, expectedOutcome: Int) {
        Assertions.assertEquals(expectedOutcome, cmp(version1, version2), "when checking with \"$version1\", \"$version2\"")
        Assertions.assertEquals(oppositeOutcome(expectedOutcome), cmp(version2, version1), "when checking with \"$version1\", \"$version2\"")
    }

    /**
     * Version tests should be commutative -  v1 > v2 -> v2 < v1
     */
    private fun oppositeOutcome(expectedOutcome: Int) = when (expectedOutcome) {
        1 -> -1
        -1 -> 1
        else -> 0
    }
}

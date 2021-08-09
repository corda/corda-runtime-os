package net.corda.v5.base.types

import com.google.common.collect.testing.SetTestSuiteBuilder
import com.google.common.collect.testing.TestIntegerSetGenerator
import com.google.common.collect.testing.features.CollectionFeature
import com.google.common.collect.testing.features.CollectionSize
import junit.framework.Test
import junit.framework.TestResult
import junit.framework.TestSuite
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream
import kotlin.streams.asStream

class NonEmptySetTestOld {

    /**
     * Builds a new NonEmptySet with provided elements for testing
     */
    private object NonEmptySetGenerator : TestIntegerSetGenerator() {
        override fun create(elements: Array<out Int?>): NonEmptySet<Int?> = NonEmptySet.copyOf(elements.asList())
    }

    /**
     * Returns a stream of Set tests from the Guava test suite, applied to our NonEmptySet class
     */
    @TestFactory
    fun suite(): Stream<DynamicTest> {
        return SetTestSuiteBuilder.using(NonEmptySetGenerator)
            .named("Guava test suite")
            .withFeatures(
                CollectionSize.SEVERAL,
                CollectionFeature.ALLOWS_NULL_VALUES,
                CollectionFeature.KNOWN_ORDER
            )
            .createTestSuite()
            .tests()
            .asSequence()
            // There are two levels of test suite, we want to flatten the tree to just a list of tests
            .flatMap { expandSuite(it) }
            .flatMap { expandSuite(it) }
            .map { test ->
                // Wrap the test in a DynamicTest object
                DynamicTest.dynamicTest(test.toString()) {

                    // Run the test
                    val testResult = TestResult()
                    test.run(testResult)

                    // Throw an exception on failure
                    if (!testResult.wasSuccessful()) {

                        // Build a string holding all the TestFailure messages
                        val allFailureMessages = testResult
                            .failures()
                            .asSequence()
                            .plus(testResult.errors())
                            .fold(StringBuilder()) { stringBuilder, message -> stringBuilder.appendLine(message) }
                            .toString()

                        // Fail the test
                        Assertions.fail<Void>(allFailureMessages)
                    }
                }
            }
            .asStream()
    }

    /**
     * Returns the tests stored inside a TestSuite or a single test if passed an individual test
     */
    private fun expandSuite(testSuiteOrTest: Test) =
        if (testSuiteOrTest is TestSuite) testSuiteOrTest.tests().asSequence() else sequenceOf(testSuiteOrTest)
}
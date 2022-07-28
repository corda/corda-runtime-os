package net.corda.uniqueness.checker.impl

import net.corda.test.util.time.AutoTickTestClock
import net.corda.uniqueness.backingstore.impl.JPABackingStore
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.mock
import java.time.Duration

/**
 * Integration tests the batched uniqueness checker implementation with the JPA based backing store
 * implementation and an associated JPA compatible DB
 */
class UniquenessCheckerImplIntegrationTests : UniquenessCheckerImplTests() {
    @BeforeEach
    override fun init() {
        /*
         * Specific clock values are important to our testing in some cases, so we use a mock time
         * facilities service which provides a clock starting at a known point in time (baseTime)
         * and will increment its current time by one second on each call. The current time can also
         * be  manipulated by tests directly via [MockTimeFacilities.advanceTime] to change this
         * between calls (e.g. to manipulate time window behavior)
         */
        testClock = AutoTickTestClock(baseTime, Duration.ofSeconds(1))

        uniquenessChecker = BatchedUniquenessCheckerImpl(mock(), testClock, JPABackingStore())
    }
}

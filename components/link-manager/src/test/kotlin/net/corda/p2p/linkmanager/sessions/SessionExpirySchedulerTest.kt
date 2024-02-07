package net.corda.p2p.linkmanager.sessions

import com.github.benmanes.caffeine.cache.Cache
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.utilities.time.Clock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.util.Random
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class SessionExpirySchedulerTest {
    private val cacheOne = mock<Cache<String, Int>>()
    private val cacheTwo = mock<Cache<String, String>>()
    private val stateManager = mock<StateManager>()
    private val clock = mock<Clock>() {
        on { instant() } doReturn Instant.ofEpochMilli(1000)
    }
    private val future = mock<ScheduledFuture<*>>()
    private val task = argumentCaptor<Runnable>()
    private val delay = argumentCaptor<Long>()
    private val unit = argumentCaptor<TimeUnit>()
    private val random = mock<Random> {
        on { nextLong(any()) } doReturn 2L
    }
    private val scheduler = mock<ScheduledExecutorService> {
        on {
            schedule(
                task.capture(),
                delay.capture(),
                unit.capture(),
            )
        } doReturn future
    }
    private val expiredState = mock<State> {
        on { metadata } doReturn Metadata(
            mapOf(
                "sourceVnode" to "O=Alice, L=London, C=GB",
                "destinationVnode" to "O=Bob, L=London, C=GB",
                "groupId" to "groupId",
                "lastSendTimestamp" to 100,
                "expiry" to 900,
            ),
        )

        on { key } doReturn "expiredStateKey"
    }
    private val validState = mock<State> {
        on { metadata } doReturn Metadata(
            mapOf(
                "sourceVnode" to "O=Carol, L=London, C=GB",
                "destinationVnode" to "O=David, L=London, C=GB",
                "groupId" to "groupId",
                "lastSendTimestamp" to 100,
                "expiry" to 3100,
            ),
        )

        on { key } doReturn "stateKey"

        on { version } doReturn 2
    }

    private val sessionExpiryScheduler = SessionExpiryScheduler(
        caches = listOf(cacheOne, cacheTwo),
        stateManager,
        clock,
        scheduler,
        random,
    )

    @Nested
    inner class CheckStateValidateAndRememberItTests {
        @Test
        fun `it will return null if the state had expired`() {
            assertThat(sessionExpiryScheduler.checkStateValidateAndRememberIt(expiredState)).isNull()
        }

        @Test
        fun `it will not schedule anything if the state had expired`() {
            sessionExpiryScheduler.checkStateValidateAndRememberIt(expiredState)

            verify(scheduler, never()).schedule(any(), any(), any())
        }

        @Test
        fun `it will return the state when it had not expired`() {
            assertThat(
                sessionExpiryScheduler.checkStateValidateAndRememberIt(
                    validState,
                ),
            ).isSameAs(validState)
        }

        @Test
        fun `it will schedule a clean up task with the correct time`() {
            sessionExpiryScheduler.checkStateValidateAndRememberIt(
                validState,
            )

            assertThat(Duration.of(delay.firstValue, unit.firstValue.toChronoUnit())).hasMillis(100)
        }

        @Test
        fun `it will not schedule any clean up for the same state`() {
            sessionExpiryScheduler.checkStateValidateAndRememberIt(
                validState,
            )
            sessionExpiryScheduler.checkStateValidateAndRememberIt(
                validState,
            )

            verify(scheduler, times(1)).schedule(any(), any(), any())
        }

        @Test
        fun `it will schedule different time if the expiry is different`() {
            val stateTwo = mock<State> {
                on { metadata } doReturn Metadata(
                    mapOf(
                        "sourceVnode" to "O=Carol, L=London, C=GB",
                        "destinationVnode" to "O=David, L=London, C=GB",
                        "groupId" to "groupId",
                        "lastSendTimestamp" to 100,
                        "expiry" to 4200,
                    ),
                )

                on { key } doReturn "stateKey"
            }
            sessionExpiryScheduler.checkStateValidateAndRememberIt(
                validState,
            )
            sessionExpiryScheduler.checkStateValidateAndRememberIt(
                stateTwo,
            )

            verify(scheduler, times(2)).schedule(any(), any(), any())
        }

        @Test
        fun `it will schedule different time if the version is different`() {
            val validMetadata = validState.metadata
            val stateTwo = mock<State> {
                on { metadata } doReturn validMetadata

                on { key } doReturn "stateKey"

                on { version } doReturn 3
            }
            sessionExpiryScheduler.checkStateValidateAndRememberIt(
                validState,
            )
            sessionExpiryScheduler.checkStateValidateAndRememberIt(
                stateTwo,
            )

            verify(scheduler, times(2)).schedule(any(), any(), any())
        }

        @Test
        fun `it will not cancel the clean up for the same state`() {
            sessionExpiryScheduler.checkStateValidateAndRememberIt(
                validState,
            )
            sessionExpiryScheduler.checkStateValidateAndRememberIt(
                validState,
            )

            verify(future, never()).cancel(any())
        }

        @Test
        fun `it will cancel if the expiry is different`() {
            val stateTwo = mock<State> {
                on { metadata } doReturn Metadata(
                    mapOf(
                        "sourceVnode" to "O=Carol, L=London, C=GB",
                        "destinationVnode" to "O=David, L=London, C=GB",
                        "groupId" to "groupId",
                        "lastSendTimestamp" to 100,
                        "expiry" to 4200,
                    ),
                )

                on { key } doReturn "stateKey"
            }
            sessionExpiryScheduler.checkStateValidateAndRememberIt(
                validState,
            )
            sessionExpiryScheduler.checkStateValidateAndRememberIt(
                stateTwo,
            )

            verify(future).cancel(any())
        }
    }

    @Nested
    inner class ForgetStateTests {
        @Test
        fun `it will delete the state from the state manager`() {
            sessionExpiryScheduler.checkStateValidateAndRememberIt(
                validState,
            )
            task.firstValue.run()

            verify(stateManager).delete(listOf(validState))
        }

        @Test
        fun `it will delete the state with the next version from the state manager if needed`() {
            val nextState = mock<State> {
                on { version } doReturn 3
                on { key } doReturn "stateKey"
            }
            whenever(validState.copy(version = 3)).doReturn(nextState)
            sessionExpiryScheduler.checkStateValidateAndRememberIt(
                validState,
                beforeUpdate = true,
            )
            task.firstValue.run()

            verify(stateManager).delete(listOf(nextState))
        }

        @Test
        fun `it will invalidate the cache`() {
            sessionExpiryScheduler.checkStateValidateAndRememberIt(
                validState,
            )
            task.firstValue.run()

            verify(cacheTwo).invalidate("stateKey")
            verify(cacheOne).invalidate("stateKey")
        }
    }

    @Nested
    inner class CheckStatesValidateAndRememberThemTest {
        @Test
        fun `it will return the correct map`() {
            val mp = mapOf(
                "one" to validState,
                "two" to expiredState,
            )

            assertThat(sessionExpiryScheduler.checkStatesValidateAndRememberThem(mp))
                .containsEntry("one", validState)
                .doesNotContainKey("two")
                .hasSize(1)
        }
    }
}

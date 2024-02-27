package net.corda.utilities.retry

import kotlin.math.pow

/**
 * Strategy to provide backoff delays when handling transient failures.
 */
interface BackoffStrategy {
    /**
     * Calculate next wait period in milliseconds for the given attempt.
     *
     * @param attempt current failed attempt, starts at 1.
     * @return wait time, in milliseconds, for the given attempt number. A negative value forces the retry to stop.
     */
    fun delay(attempt: Int): Long
}

/**
 * Fixed backoff strategy, delay between retries remains constant between attempts.
 *
 * @param delay the constant delay, in milliseconds, to be applied on each attempt.
 */
class Fixed(private val delay: Long = 1000L) : BackoffStrategy {
    override fun delay(attempt: Int) = delay
}

/**
 * Fixed sequence backoff strategy, delay between retries is fixed and pre-configured for any given attempt number.
 *
 * @param delays the array of delays, in milliseconds, to be returned for each attempt.
 */
class FixedSequence(private val delays: List<Long>) : BackoffStrategy {
    override fun delay(attempt: Int) =
        // Halt the retry process if there are not enough delays configured.
        if (attempt > delays.size) {
            -1
        } else {
            delays[attempt - 1]
        }
}

/**
 * Linear backoff strategy, the delay between retries increases linearly with each attempt.
 *
 * @param growthFactor constant increment added to the delay, in milliseconds, with each attempt.
 */
class Linear(private val growthFactor: Long = 1000L) : BackoffStrategy {
    override fun delay(attempt: Int) = growthFactor * attempt
}

/**
 * Exponential backoff strategy, the delay between retries increases exponentially with each attempt.
 *
 * @param base the base value for exponential growth.
 * @param growthFactor the multiplier, in milliseconds, to scale the exponential growth.
 */
class Exponential(private val base: Double = 2.0, private val growthFactor: Long = 1000L) : BackoffStrategy {
    override fun delay(attempt: Int) = (base.pow(attempt)).toLong() * growthFactor
}

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
 * Constant backoff strategy, delay between retries remains constant between attempts.
 *
 * @param delay the constant delay to be applied on each attempt.
 */
class Constant(private val delay: Long = 1000L) : BackoffStrategy {
    override fun delay(attempt: Int) = delay
}

/**
 * Linear backoff strategy, the delay between retries increases linearly with each attempt.
 *
 * @param growthFactor constant increment added to the delay with each attempt.
 */
class Linear(private val growthFactor: Long = 1000L) : BackoffStrategy {
    override fun delay(attempt: Int) = growthFactor * attempt
}

/**
 * Exponential backoff strategy, the delay between retries increases exponentially with each attempt.
 *
 * @param base the base value for exponential growth.
 * @param growthFactor the multiplier to scale the exponential growth.
 */
class Exponential(private val base: Double = 2.0, private val growthFactor: Long = 1000L) : BackoffStrategy {
    override fun delay(attempt: Int) = (base.pow(attempt)).toLong() * growthFactor
}

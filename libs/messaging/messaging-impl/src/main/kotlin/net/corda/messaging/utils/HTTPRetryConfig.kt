package net.corda.messaging.utils

import kotlin.reflect.KClass

/**
 * Configuration class for HTTP retry parameters.
 *
 * @property times The number of a times a retry will be attempted. Default is 3
 * @property initialDelay The initial delay (in milliseconds) before the first retry. Default is 100ms
 * @property factor The multiplier used to increase the delay for each subsequent retry. Default is 2.0
 * @property retryOn A set of exception classes that should trigger a retry when caught.
 *                   If an exception not in this list is caught, it will be propagated immediately without retrying.
 *                   Default is the generic [Exception] class, meaning all exceptions will trigger a retry.
 */
data class HTTPRetryConfig(
    val times: Int = 3,
    val initialDelay: Long = 100,
    val factor: Double = 2.0,
    val retryOn: Set<KClass<out Exception>> = setOf(Exception::class)
) {
    class Builder {
        private var times: Int = 3
        private var initialDelay: Long = 100
        private var factor: Double = 2.0
        private var retryOn: Set<KClass<out Exception>> = setOf(Exception::class)

        fun times(times: Int) = apply { this.times = times }
        fun initialDelay(delay: Long) = apply { this.initialDelay = delay }
        fun factor(factor: Double) = apply { this.factor = factor }
        fun retryOn(vararg exceptions: KClass<out Exception>) = apply { this.retryOn = exceptions.toSet() }

        fun build(): HTTPRetryConfig {
            return HTTPRetryConfig(times, initialDelay, factor, retryOn)

        }
    }
}

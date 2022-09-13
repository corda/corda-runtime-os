package net.corda.v5.application.uniqueness.model

import java.time.Instant

/**
 * Representation of the result of a uniqueness check request.
 *
 * @property resultTimestamp The timestamp when the request was processed.
 */
interface UniquenessCheckResult {
    val resultTimestamp: Instant
}

/**
 * This result will be returned by the uniqueness checker if the request was successful.
 */
interface UniquenessCheckResultSuccess : UniquenessCheckResult

/**
 * This result will be returned by the uniqueness checker if the request was unsuccessful.
 *
 * @property error Specific details about why the request was unsuccessful.
 */
interface UniquenessCheckResultFailure : UniquenessCheckResult {
    val error: UniquenessCheckError
}

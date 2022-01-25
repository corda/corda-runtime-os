package net.corda.messagebus.db.persistence

/**
 * @property partition the partition to fetch records from.
 * @param startOffset the offset after which records can be fetched (inclusive).
 * @param endOffset the offset up to which records can be fetched (inclusive).
 * @param limit the maximum number of records to be fetched.
 */
data class FetchWindow(val partition: Int, val startOffset: Long, val endOffset: Long, val limit: Int)

package net.corda.rocks.db.api

/**
 * Internal service to iterate through a rocks db table
 */
interface QueryProcessor {

    /**
     * Process a key value bytes pair read from a rocks db table
     * @param keyBytes bytes read for a key
     * @param valueBytes bytes read for a value
     * @return false if iteration should stop, true to continue iterating the next key value pair.
     */
    fun process(keyBytes: ByteArray, valueBytes: ByteArray)
}

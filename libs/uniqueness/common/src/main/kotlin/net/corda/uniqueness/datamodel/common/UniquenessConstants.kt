package net.corda.uniqueness.datamodel.common

/**
 * Common constants.
 *
 * BE CAREFUL WHEN CHANGING THESE AS SOME OF THESE CONSTANTS MAY CHANGE DATABASE FIELD SIZES
 * AND / OR THE STRUCTURE OF VALUES IN THE DATABASE, RESULTING IN DB UPGRADE IMPLICATIONS!
 */
object UniquenessConstants {
    /**
     * Specifies the maximum supported transaction algorithm length.
     *
     * CHANGING THIS VALUE WILL CHANGE THE SIZE OF DATABASE FIELDS STORING TRANSACTION ALGORITHM IDS
     */
    const val TRANSACTION_ID_ALGO_LENGTH = 8

    /**
     * Specifies the maximum supported transaction hash length.
     *
     * CHANGING THIS VALUE WILL CHANGE THE SIZE OF DATABASE FIELDS STORING TRANSACTION IDS
     */
    const val TRANSACTION_ID_LENGTH = 64

    /**
     * Specifies the maximum supported rejected transaction error details length.
     *
     * CHANGING THIS VALUE WILL CHANGE THE SIZE LIMIT OF REJECTED TRANSACTION ERROR DETAILS
     */
    const val REJECTED_TRANSACTION_ERROR_DETAILS_LENGTH = 1024

    /**
     * Character representation of accepted results, used in the backing store
     */
    const val RESULT_ACCEPTED_REPRESENTATION = 'A'

    /**
     * Character representation of rejected results, used in the backing store
     */
    const val RESULT_REJECTED_REPRESENTATION = 'R'

    /**
     * Specifies size for Hibernate batch processing.
     */
    const val HIBERNATE_JDBC_BATCH_SIZE = 50
}

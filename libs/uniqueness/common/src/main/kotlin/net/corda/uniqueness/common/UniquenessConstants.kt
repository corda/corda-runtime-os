package net.corda.uniqueness.common

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
}

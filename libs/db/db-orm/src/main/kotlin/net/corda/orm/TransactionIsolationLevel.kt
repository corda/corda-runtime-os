package net.corda.orm

/**
 * Transaction isolation level enum
 * Default: READ_COMMITTED
 *
 * @constructor Create empty Transaction isolation level
 */
enum class TransactionIsolationLevel {
    NONE,
    READ_UNCOMMITTED,
    READ_COMMITTED,
    REPEATABLE_READ,
    SERIALIZABLE;

    /**
     * The JDBC constant value of the same name but prefixed with TRANSACTION_ defined in [java.sql.Connection].
     */
    val jdbcString = "TRANSACTION_$name"
    val jdbcValue: Int = java.sql.Connection::class.java.getField(jdbcString).get(null) as Int

    companion object {
        @JvmField
        val default = READ_COMMITTED
    }
}

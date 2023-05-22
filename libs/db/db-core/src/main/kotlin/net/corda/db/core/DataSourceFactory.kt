package net.corda.db.core

import java.time.Duration

interface DataSourceFactory {
    /**
     * Create a new [CloseableDataSource] with the given configuration
     *
     * @param driverClass type of JDBC driver to use.
     * @param jdbcUrl
     * @param username
     * @param password
     * @param isAutoCommit default auto-commit behaviour.
     * @param isReadOnly when set, and supported by the database, the pool will return read-only connections. Default - false.
     * @param maximumPoolSize the maximum number of connection in the pool. Default - 10
     * @param minimumPoolSize the minimum number of connections in the pool.
     *      Defaults - the same as the maximum number of connections configured ([maximumPoolSize])
     * @param idleTimeout the maximum amount of time a connection is allowed to be idle before being removed from the pool.
     *      If [minimumPoolSize] is equal to [maximumPoolSize], then connections will never be removed.
     *      Default - 2 minutes.
     * @param maxLifetime the maximum lifetime of a connection in the pool.
     *      An in-use connection will never be retired, only when it is closed will it then be removed.
     *      This should be set several seconds shorter than any database or infrastructure imposed connection time limit.
     *      Default - 30 minutes.
     * @param keepaliveTime This property controls how frequently the pool will attempt to keep a connection alive,
     *      in order to prevent it from being timed out by the database or network infrastructure.
     *      This value must be less than the [maxLifetime] value.
     *      A value in the range of minutes is most desirable.
     *      Default - 0 (disabled).
     * @param validationTimeout maximum amount of time that a connection will be tested for aliveness. Default - 5 seconds.
     */
    @Suppress("LongParameterList")
    fun create(
        driverClass: String,
        jdbcUrl: String,
        username: String,
        password: String,
        isAutoCommit: Boolean = false,
        isReadOnly: Boolean = false,
        maximumPoolSize: Int = 10,
        minimumPoolSize: Int? = null,
        idleTimeout: Duration = Duration.ofMinutes(2),
        maxLifetime: Duration = Duration.ofMinutes(30),
        keepaliveTime: Duration = Duration.ZERO,
        validationTimeout: Duration = Duration.ofSeconds(5),
    ): CloseableDataSource
}

package net.corda.db.core

import java.time.Duration

interface DataSourceFactory {
    /**
     * Create a new [CloseableDataSource] with the given configuration
     *
     * @param enablePool enables client side connection pooling.
     * @param driverClass type of JDBC driver to use.
     * @param jdbcUrl
     * @param username
     * @param password
     * @param isAutoCommit default auto-commit behaviour.
     * @param isReadOnly when set, and supported by the database, the pool will return read-only connections. Default - false.
     * @param maximumPoolSize the maximum number of connection in the pool.
     *      Default - 10.
     *      Ignored when pooling disabled.
     * @param minimumPoolSize the minimum number of connections in the pool.
     *      Defaults - the same as the maximum number of connections configured ([maximumPoolSize]).
     *      Ignored when pooling disabled.
     * @param idleTimeout the maximum amount of time a connection is allowed to be idle before being removed from the pool.
     *      If [minimumPoolSize] is equal to [maximumPoolSize], then connections will never be removed and this
     *      parameter is ignored.
     *      Default - 2 minutes.
     *      Ignored when pooling disabled.
     * @param maxLifetime the maximum lifetime of a connection in the pool.
     *      An in-use connection will never be retired, only when it is closed will it then be removed.
     *      This should be set several seconds shorter than any database or infrastructure imposed connection time limit.
     *      Default - 30 minutes.
     *      Ignored when pooling disabled.
     * @param keepaliveTime This property controls how frequently the pool will attempt to keep a connection alive,
     *      in order to prevent it from being timed out by the database or network infrastructure.
     *      This value must be less than the [maxLifetime] value.
     *      A value in the range of minutes is most desirable.
     *      Default - 0 (disabled).
     *      Ignored when pooling disabled.
     * @param validationTimeout maximum amount of time that a connection will be tested for aliveness.
     *      Default - 5 seconds.
     *      Ignored when pooling disabled.
     */
    @Suppress("LongParameterList")
    fun create(
        enablePool: Boolean,
        driverClass: String,
        jdbcUrl: String,
        username: String,
        password: String,
        isAutoCommit: Boolean = false,
        isReadOnly: Boolean = false,
        maximumPoolSize: Int,
        minimumPoolSize: Int?,
        idleTimeout: Duration,
        maxLifetime: Duration,
        keepaliveTime: Duration,
        validationTimeout: Duration,
    ): CloseableDataSource
}

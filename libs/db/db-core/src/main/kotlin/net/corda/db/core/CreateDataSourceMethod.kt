package net.corda.db.core

import java.time.Duration

@Suppress("LongParameterList")
fun createUnpooledDataSource(
    driverClass: String,
    jdbcUrl: String,
    username: String,
    password: String,
    datasourceFactory: DataSourceFactory = DataSourceFactoryImpl(),
    maximumPoolSize: Int = 5,
): CloseableDataSource {
    return datasourceFactory.create(
        enablePool = false,
        driverClass = driverClass,
        jdbcUrl = jdbcUrl,
        username = username,
        password = password,
        maximumPoolSize = maximumPoolSize,
        minimumPoolSize = 1,
        idleTimeout = Duration.ofMinutes(2),
        maxLifetime = Duration.ofMinutes(30),
        keepaliveTime = Duration.ZERO,
        validationTimeout = Duration.ofSeconds(5),
    )
}
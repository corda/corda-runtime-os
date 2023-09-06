package net.corda.db.core

import java.time.Duration

@Suppress("LongParameterList")
fun createDataSource(
    driverClass: String,
    jdbcUrl: String,
    username: String,
    password: String,
    datasourceFactory: DataSourceFactory = HikariDataSourceFactory(),
    maximumPoolSize: Int = 5,
): CloseableDataSource {
    return datasourceFactory.create(
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
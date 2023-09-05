package net.corda.db.core

import java.time.Duration

class BaseDataSourceFactory(
    private val datasourceFactory: DataSourceFactory
){

    fun create(
        driverClass: String,
        jdbcUrl: String,
        username: String,
        password: String,
        maximumPoolSize: Int = 5
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
}
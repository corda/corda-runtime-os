package net.corda.db.core

import java.time.Duration

class PostgresDataSourceFactory(
    private val datasourceFactory: DataSourceFactory = HikariDataSourceFactory()
) {
    fun create(
        jdbcUrl: String,
        username: String,
        password: String,
        maximumPoolSize: Int = 5
    ): CloseableDataSource {
        return datasourceFactory.create(
            driverClass = "org.postgresql.Driver",
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

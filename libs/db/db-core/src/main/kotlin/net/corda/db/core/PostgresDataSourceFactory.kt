package net.corda.db.core

class PostgresDataSourceFactory(
    private val datasourceFactory: DataSourceFactory = HikariDataSourceFactory()
) {
    fun create(
        jdbcUrl: String,
        username: String,
        password: String,
        maximumPoolSize: Int = 3
    ): CloseableDataSource {
        return datasourceFactory.create(
            driverClass = "org.postgresql.Driver",
            jdbcUrl = jdbcUrl,
            username = username,
            password = password,
            maximumPoolSize = maximumPoolSize
        )
    }
}

package net.corda.db.core

class PostgresDataSourceFactory(
    factory: DataSourceFactory = HikariDataSourceFactory()
) : BaseDataSourceFactory(factory) {
    override val driverClass = "org.postgresql.Driver"
}

package net.corda.db.core
class SQLDataSourceFactory(
    factory: DataSourceFactory = HikariDataSourceFactory()
) : BaseDataSourceFactory(factory) {
    override val driverClass = "com.microsoft.sqlserver.jdbc.SQLServerDriver"
}

package net.corda.db.core

class InMemoryDataSourceFactory(
    private val datasourceFactory: DataSourceFactory = HikariDataSourceFactory()
) {
    fun create(dbName: String): CloseableDataSource {
        return datasourceFactory.create(
            "org.hsqldb.jdbc.JDBCDriver",
            "jdbc:hsqldb:mem:$dbName",
            "sa",
            ""
        )
    }
}


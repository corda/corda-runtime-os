package net.corda.db.core

import javax.sql.DataSource

class InMemoryDataSourceFactory(
    private val datasourceFactory: DataSourceFactory = HikariDataSourceFactory()
) {
    fun create(dbName: String): DataSource {
        return datasourceFactory.create(
            "org.hsqldb.jdbc.JDBCDriver",
            "jdbc:hsqldb:mem:$dbName",
            "sa",
            ""
        )
    }
}


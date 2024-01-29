package net.corda.db.core

import java.time.Duration

class InMemoryDataSourceFactory(
    private val datasourceFactory: DataSourceFactory = DataSourceFactoryImpl()
) {
    fun create(dbName: String): CloseableDataSource {
        return datasourceFactory.create(
            enablePool = true,
            "org.hsqldb.jdbc.JDBCDriver",
            "jdbc:hsqldb:mem:$dbName",
            "sa",
            "",
            maximumPoolSize = 10,
            minimumPoolSize = null,
            idleTimeout = Duration.ofMinutes(2),
            maxLifetime = Duration.ofMinutes(30),
            keepaliveTime = Duration.ZERO,
            validationTimeout = Duration.ofSeconds(5),
        )
    }
}


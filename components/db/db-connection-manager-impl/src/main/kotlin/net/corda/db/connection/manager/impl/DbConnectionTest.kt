package net.corda.db.connection.manager.impl

import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Checks the database connection
 */
class DbConnectionTest {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Checks that it is possible to connect to the cluster database using the [dataSource].
     *
     * Returns true if the DB is available.
     */
    fun isDatabaseConnectionUp(dataSource: DataSource): Boolean = try {
        dataSource.connection.close()
        true
    } catch (e: Exception) {
        logger.warn("Failed to connect to DB", e)
        false
    }
}
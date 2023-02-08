package net.corda.db.connection.manager.impl

import javax.sql.DataSource

/**
 * Checks that it is possible to connect to the cluster database using the [dataSource].
 *
 * Returns true if the DB is available.
 */
fun isDatabaseConnectionUp(dataSource: DataSource): Boolean = try {
    dataSource.connection.close()
    true
} catch (e: Exception) {
    false
}

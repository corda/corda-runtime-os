package net.corda.configuration.write.impl.dbutils

import net.corda.libs.configuration.SmartConfig
import java.sql.SQLException

/** Encapsulates database-related functionality, so that it can be stubbed during tests. */
interface DBUtils {
    /**
     * Checks that it's possible to connect to the cluster database using the [config].
     *
     * @throws SQLException If the cluster database cannot be connected to.
     */
    fun checkClusterDatabaseConnection(config: SmartConfig)
}
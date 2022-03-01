package net.corda.virtualnode.write.db.impl.writer

import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.ConfigKeys.DB_PASS
import net.corda.schema.configuration.ConfigKeys.DB_USER

/**
 * Virtual node DB connection data.
 *
 * @property name Connection name
 * @property privilege Connection's DB privilege
 * @property config Connection configuration
 * @property description Connection's description
 */
class DbConnection(val name: String, val privilege: DbPrivilege, val config: SmartConfig, val description: String) {

    /**
     * Returns DB user
     * @return DB user
     */
    fun getUser(): String? =
        if (config.hasPath(DB_USER)) config.getString(DB_USER) else null

    /**
     * Returns DB password
     * @return DB password
     */
    fun getPassword(): String? =
        if (config.hasPath(DB_PASS)) config.getString(DB_PASS) else null
}
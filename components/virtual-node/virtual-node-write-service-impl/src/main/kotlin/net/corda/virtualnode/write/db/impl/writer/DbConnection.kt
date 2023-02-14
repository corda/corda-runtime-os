package net.corda.virtualnode.write.db.impl.writer

import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig

/**
 * Represents a Virtual Node DB connection
 *
 * @property name Connection name
 * @property privilege Connection's DB privilege
 * @property config Connection configuration
 * @property description Connection's description
 */

internal interface DbConnection {
    val name: String
    val privilege: DbPrivilege
    val config: SmartConfig
    val description: String

    /**
     * Returns DB user
     * @return DB user
     */
    fun getUser(): String?

    /**
     * Returns DB password
     * @return DB password
     */
    fun getPassword(): String?
}
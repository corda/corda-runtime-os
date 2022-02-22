package net.corda.virtualnode.write.db.impl.writer

import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig

/**
 * Virtual node DB connection data.
 *
 * @property name Connection name
 * @property privilege Connection's DB privilege
 * @property config Connection configuration
 * @property description Connection's description
 * @property user DB user for this connection
 * @property password DB password for this connection
 */
class DbConnection(val name: String, val privilege: DbPrivilege, val config: SmartConfig, val description: String? = null,
                   val user: String? = null, val password: String? = null) {
}
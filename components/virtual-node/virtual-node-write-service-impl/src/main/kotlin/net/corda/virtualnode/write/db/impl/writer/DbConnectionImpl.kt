package net.corda.virtualnode.write.db.impl.writer

import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.DatabaseConfig.DB_PASS
import net.corda.schema.configuration.DatabaseConfig.DB_USER


class DbConnectionImpl(
    override val name: String,
    override val privilege: DbPrivilege,
    override val config: SmartConfig,
    override val description: String
) : DbConnection {

    override fun getUser(): String? =
        if (config.hasPath(DB_USER)) config.getString(DB_USER) else null

    override fun getPassword(): String? =
        if (config.hasPath(DB_PASS)) config.getString(DB_PASS) else null
}
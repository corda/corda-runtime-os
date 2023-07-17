package net.corda.virtualnode.write.db.impl.writer

import net.corda.db.connection.manager.DatasourceConfigOverrides
import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.DatabaseConfig.DB_PASS
import net.corda.schema.configuration.DatabaseConfig.DB_USER

// * If it's a Corda managed DB then config is empty and datasourceOverrides populated
// * If it's not a Corda managed DB then datasourceOverrides is null and config populated
// TODO From the below class it seems we are using the config if it is a non Corda managed DB
//  and then the overrides the user and the password if it is. So maybe we could subtype this type to make it more clear
//  per use.
class DbConnectionImpl(
    override val name: String,
    override val privilege: DbPrivilege,
    override val config: SmartConfig, // If there are secrets in here, when it comes to write to the DB it will resolve the secrets.
    override val datasourceOverrides: DatasourceConfigOverrides?,
    override val description: String
) : DbConnection {

    private val isPlatformManagedDb = datasourceOverrides != null

    override fun getUser(): String? =
        if (isPlatformManagedDb) {
            datasourceOverrides!!.username
        } else {
            if (config.hasPath(DB_USER)) config.getString(DB_USER) else null
        }

    override fun getPassword(): String? =
        if (isPlatformManagedDb) {
            datasourceOverrides!!.password
        } else {
            if (config.hasPath(DB_PASS)) config.getString(DB_PASS) else null
        }
}
package net.corda.virtualnode.write.db.impl

import net.corda.db.connection.manager.DbAdmin
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import javax.sql.DataSource
import javax.xml.crypto.Data

/**
 * DbAdmin for Virtual Nodes database.
 */
class VirtualNodesDbAdmin(private val dbConnectionManager: DbConnectionManager) : DbAdmin() {
    override fun bindDataSource() = checkNotNull(
        dbConnectionManager.getDataSource(
            CordaDb.VirtualNodes.persistenceUnitName,
            DbPrivilege.DDL
        )
    ) { "Virtual nodes DDL data source not found." }
}

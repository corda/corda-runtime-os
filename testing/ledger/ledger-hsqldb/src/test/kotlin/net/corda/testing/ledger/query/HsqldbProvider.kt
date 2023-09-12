package net.corda.testing.ledger.query

import net.corda.orm.DatabaseType
import net.corda.orm.DatabaseTypeProvider

object HsqldbProvider : DatabaseTypeProvider {
    override val databaseType: DatabaseType
        get() = DatabaseType.HSQLDB
}

package net.corda.ledger.persistence.query.impl.parsing

import net.corda.orm.DatabaseType
import net.corda.orm.DatabaseTypeProvider

object PostgresProvider : DatabaseTypeProvider {
    override val databaseType: DatabaseType
        get() = DatabaseType.POSTGRES
}

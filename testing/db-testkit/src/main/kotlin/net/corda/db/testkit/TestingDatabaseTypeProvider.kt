package net.corda.db.testkit

import net.corda.orm.DatabaseType
import net.corda.orm.DatabaseTypeProvider
import org.osgi.service.component.annotations.Component

@Suppress("unused")
@Component
class TestingDatabaseTypeProvider : DatabaseTypeProvider {
    override val databaseType: DatabaseType = if (DbUtils.isInMemory) {
        DatabaseType.HSQLDB
    } else {
        DatabaseType.POSTGRESQL
    }
}

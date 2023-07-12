package net.corda.testing.driver.db

import net.corda.orm.DatabaseType
import net.corda.orm.DatabaseType.HSQLDB
import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.HSQLDB_TYPE
import net.corda.testing.driver.sandbox.DRIVER_SERVICE
import net.corda.testing.driver.sandbox.DRIVER_SERVICE_RANKING
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@Suppress("unused")
@Component(
    service = [ DatabaseTypeProvider::class ],
    property = [ HSQLDB_TYPE, DRIVER_SERVICE ]
)
@ServiceRanking(DRIVER_SERVICE_RANKING)
class InMemoryDatabaseTypeProvider : DatabaseTypeProvider {
    override val databaseType: DatabaseType
        get() = HSQLDB
}

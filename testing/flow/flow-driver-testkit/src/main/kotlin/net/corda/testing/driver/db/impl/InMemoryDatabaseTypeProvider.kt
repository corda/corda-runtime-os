package net.corda.testing.driver.db.impl

import net.corda.orm.DatabaseType
import net.corda.orm.DatabaseTypeProvider
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@Component(property = [ DRIVER_SERVICE ])
@ServiceRanking(1)
class InMemoryDatabaseTypeProvider : DatabaseTypeProvider {
    override val databaseType: DatabaseType
        get() = DatabaseType.HSQLDB
}

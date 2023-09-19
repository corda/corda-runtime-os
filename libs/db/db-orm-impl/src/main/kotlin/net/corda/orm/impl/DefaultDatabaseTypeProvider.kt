package net.corda.orm.impl

import net.corda.orm.DatabaseType
import net.corda.orm.DatabaseType.POSTGRES
import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.POSTGRES_TYPE
import org.osgi.service.component.annotations.Component

@Suppress("unused")
@Component(service = [ DatabaseTypeProvider::class ], property = [ POSTGRES_TYPE ])
class DefaultDatabaseTypeProvider : DatabaseTypeProvider {
    override val databaseType: DatabaseType
        get() = POSTGRES
}

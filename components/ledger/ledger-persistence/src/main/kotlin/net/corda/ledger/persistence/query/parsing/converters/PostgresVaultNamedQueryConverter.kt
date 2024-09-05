package net.corda.ledger.persistence.query.parsing.converters

import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.POSTGRES_TYPE_FILTER
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [VaultNamedQueryConverter::class])
class PostgresVaultNamedQueryConverter(delegate: VaultNamedQueryConverter) : VaultNamedQueryConverter by delegate {
    @Activate
    constructor(
        @Reference(target = POSTGRES_TYPE_FILTER)
        databaseTypeProvider: DatabaseTypeProvider
    ) : this(MyPostgresVaultNamedQueryConverter(databaseTypeProvider))
}

package net.corda.ledger.persistence.utxo.impl

import net.corda.ledger.libs.utxo.impl.PostgresUtxoQueryProvider
import net.corda.ledger.libs.utxo.impl.UtxoQueryProvider
import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.POSTGRES_TYPE_FILTER
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(service = [ UtxoQueryProvider::class ])
class PostgresUtxoOsgiQueryProvider(
    databaseTypeProvider: DatabaseTypeProvider,
    delegate: UtxoQueryProvider
) : UtxoQueryProvider by delegate {
    init {
        LoggerFactory.getLogger(this::class.java).debug { "Activated for ${databaseTypeProvider.databaseType}" }
    }

    @Activate constructor(
        @Reference(target = POSTGRES_TYPE_FILTER)
        databaseTypeProvider: DatabaseTypeProvider
    ) : this(databaseTypeProvider, PostgresUtxoQueryProvider())
}

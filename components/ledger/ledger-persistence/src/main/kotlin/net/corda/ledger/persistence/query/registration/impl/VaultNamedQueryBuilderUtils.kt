package net.corda.ledger.persistence.query.registration.impl

import net.corda.ledger.persistence.query.data.VaultNamedQuery
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("net.corda.ledger.persistence.query.impl.VaultNamedQueryBuilderUtils")

fun logQueryRegistration(name: String, query: VaultNamedQuery.ParsedQuery) {
    when {
        log.isTraceEnabled -> log.trace(
            "Registering vault named query with name: $name, original query: ${query.originalQuery.replace(
                "\n",
                " "
            )
            }, parsed query: ${query.query}"
        )
        log.isDebugEnabled -> log.debug("Registering vault named query with name: $name")
    }
}

package net.corda.ledger.lib.dependencies.db

import javax.persistence.Persistence

object DbDependencies {
    val cryptoEntityManagerFactory = Persistence.createEntityManagerFactory("crypto")
    val cryptoEntityManagerFactory2 = Persistence.createEntityManagerFactory("crypto")
    val ledgerEntityManagerFactory = Persistence.createEntityManagerFactory("ledger")
}
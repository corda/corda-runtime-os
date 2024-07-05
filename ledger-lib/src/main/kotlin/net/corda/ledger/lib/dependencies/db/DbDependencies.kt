package net.corda.ledger.lib.dependencies.db

import javax.persistence.Persistence

object DbDependencies {
    val entityManagerFactory = Persistence.createEntityManagerFactory("whatever")
}
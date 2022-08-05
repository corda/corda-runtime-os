package net.corda.crypto.persistence

import net.corda.lifecycle.Lifecycle
import javax.persistence.EntityManagerFactory

interface CryptoConnectionsFactory : Lifecycle {
    fun getEntityManagerFactory(tenantId: String): EntityManagerFactory
}
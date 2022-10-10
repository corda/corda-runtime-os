package net.corda.crypto.service.persistence

import net.corda.lifecycle.Lifecycle
import javax.persistence.EntityManagerFactory

interface CryptoConnectionsFactory : Lifecycle {
    fun getEntityManagerFactory(tenantId: String): EntityManagerFactory
}
package net.corda.ledger.lib.impl.stub.signing

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.softhsm.SigningRepository
import net.corda.crypto.softhsm.SigningRepositoryFactory
import net.corda.crypto.softhsm.impl.SigningRepositoryImpl
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.ledger.lib.common.Constants.TENANT_ID
import javax.persistence.EntityManagerFactory

class StubSigningRepositoryFactoryImpl(
    private val entityManagerFactory: EntityManagerFactory,
    private val keyEncodingService: KeyEncodingService,
    private val digestService: PlatformDigestService,
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory
) : SigningRepositoryFactory {
    override fun getInstance(tenantId: String): SigningRepository {
        return SigningRepositoryImpl(
            entityManagerFactory = entityManagerFactory,
            tenantId = TENANT_ID,
            keyEncodingService = keyEncodingService,
            digestService = digestService,
            layeredPropertyMapFactory = layeredPropertyMapFactory
        )
    }
}
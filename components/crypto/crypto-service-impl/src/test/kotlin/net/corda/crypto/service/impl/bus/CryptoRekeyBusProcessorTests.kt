package net.corda.crypto.service.impl.bus

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoService
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.crypto.softhsm.WrappingRepositoryFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.UUID

class CryptoRekeyBusProcessorTests {
    companion object {
        private val configEvent = ConfigChangedEvent(
            setOf(ConfigKeys.CRYPTO_CONFIG),
            mapOf(
                ConfigKeys.CRYPTO_CONFIG to
                        SmartConfigFactory.createWithoutSecurityServices().create(
                            createDefaultCryptoConfig("pass", "salt")
                        )
            )
        )
    }

    private lateinit var factory: TestServicesFactory
    private lateinit var tenantId: String
    private lateinit var cryptoRekeyBusProcessor: CryptoRekeyBusProcessor
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService
    private lateinit var wrappingRepositoryFactory: WrappingRepositoryFactory
    private lateinit var publisherFactory: PublisherFactory

    @BeforeAll
    fun setup() {

        val cryptoService: CryptoService = mock<CryptoService> { }
        virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> { }

        wrappingRepositoryFactory = mock<WrappingRepositoryFactory> {}
        publisherFactory = mock<PublisherFactory> {}
        cryptoRekeyBusProcessor = CryptoRekeyBusProcessor(cryptoService, virtualNodeInfoReadService,
            wrappingRepositoryFactory, publisherFactory, configEvent.config.toCryptoConfig())


        tenantId = UUID.randomUUID().toString()
        factory = TestServicesFactory()

        CryptoConsts.Categories.all.forEach {
            factory.tenantInfoService.populate(tenantId, it, factory.cryptoService)
        }
    }


    @Test
    fun `do a mocked key rotation`() {
        // to be implemented
    }

    @Test
    fun `limit for key rotation operations is reflected`() {
        // to be implemented
    }

}

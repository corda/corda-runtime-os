package net.corda.processors.crypto.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.crypto.persistence.HSMStore
import net.corda.crypto.persistence.SigningKeyStore
import net.corda.crypto.persistence.WrappingKeyStore
import net.corda.crypto.service.CryptoFlowOpsBusService
import net.corda.crypto.service.CryptoOpsBusService
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.HSMRegistrationBusService
import net.corda.crypto.service.HSMService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.softhsm.SoftCryptoServiceProvider
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.TestMethodOrder
import org.mockito.kotlin.mock

@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CryptoProcessorImplTest {

    lateinit var cryptoProcessor: CryptoProcessorImpl

    val dependentComponents = setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        LifecycleCoordinatorName.forComponent<CryptoConnectionsFactory>(),
        LifecycleCoordinatorName.forComponent<WrappingKeyStore>(),
        LifecycleCoordinatorName.forComponent<SigningKeyStore>(),
        LifecycleCoordinatorName.forComponent<HSMStore>(),
        LifecycleCoordinatorName.forComponent<SigningServiceFactory>(),
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        LifecycleCoordinatorName.forComponent<CryptoOpsBusService>(),
        LifecycleCoordinatorName.forComponent<SoftCryptoServiceProvider>(),
        LifecycleCoordinatorName.forComponent<CryptoServiceFactory>(),
        LifecycleCoordinatorName.forComponent<HSMService>(),
        LifecycleCoordinatorName.forComponent<HSMRegistrationBusService>(),
        LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
        LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()
    )
    val cryptoOpsClientAndDependentComponents = setOf(
        LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
        LifecycleCoordinatorName.forComponent<CryptoFlowOpsBusService>()
    )

    val lifecycleTest = LifecycleTest {
        cryptoProcessor =
            CryptoProcessorImpl(
                coordinatorFactory,
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock()
            )
        // We need to test `CryptoProcessorImpl` lifecycle bootstrapping. `CryptoProcessorImpl` is not a
        // `Lifecycle` so test it by grabbing its lifecycle coordinator.
        cryptoProcessor.lifecycleCoordinator
    }.also { lifecycleTest ->
        (dependentComponents + cryptoOpsClientAndDependentComponents).forEach {
            lifecycleTest.addDependency(it)
        }
    }

    init {
        cryptoProcessor.start(mock())
    }

    @Test
    @Order(5)
    fun `fresh lifecycle bootstrapping brings crypto processor UP`() {
        dependentComponents.forEach(lifecycleTest::bringDependencyUp)
        cryptoOpsClientAndDependentComponents.forEach(lifecycleTest::bringDependencyUp)
        assertEquals(LifecycleStatus.UP, lifecycleTest.testClass.status)
    }

    @Test
    @Order(10)
    fun `cryptoOpsClientAndDependentComponents going UP while dependentComponents are UP brings crypto processor back UP`() {
        cryptoOpsClientAndDependentComponents.forEach(lifecycleTest::bringDependencyDown)
        assertEquals(LifecycleStatus.DOWN, lifecycleTest.testClass.status)
        cryptoOpsClientAndDependentComponents.forEach(lifecycleTest::bringDependencyUp)
        assertEquals(LifecycleStatus.UP, lifecycleTest.testClass.status)
    }

    @Test
    @Order(15)
    fun `dependentComponents going UP while cryptoOpsClientAndDependentComponents are UP brings crypto processor back UP`() {
        dependentComponents.forEach(lifecycleTest::bringDependencyDown)
        cryptoOpsClientAndDependentComponents.forEach(lifecycleTest::verifyIsUp)
        assertEquals(LifecycleStatus.DOWN, lifecycleTest.testClass.status)
        cryptoOpsClientAndDependentComponents.forEach(lifecycleTest::verifyIsUp)
        dependentComponents.forEach(lifecycleTest::bringDependencyUp)
        cryptoOpsClientAndDependentComponents.forEach(lifecycleTest::verifyIsUp)
        assertEquals(LifecycleStatus.UP, lifecycleTest.testClass.status)
    }

    @Test
    @Order(20)
    fun `cryptoOpsClientAndDependentComponents going UP while dependentComponents are DOWN should keep processor DOWN`() {
        dependentComponents.forEach(lifecycleTest::bringDependencyDown)
        cryptoOpsClientAndDependentComponents.forEach(lifecycleTest::bringDependencyDown)
        assertEquals(LifecycleStatus.DOWN, lifecycleTest.testClass.status)
        cryptoOpsClientAndDependentComponents.forEach(lifecycleTest::bringDependencyUp)
        assertEquals(LifecycleStatus.DOWN, lifecycleTest.testClass.status)
    }

    @Test
    @Order(20)
    fun `dependentComponents going UP while cryptoOpsClientAndDependentComponents are DOWN should keep processor DOWN`() {
        cryptoOpsClientAndDependentComponents.forEach(lifecycleTest::bringDependencyDown)
        dependentComponents.forEach(lifecycleTest::bringDependencyDown)
        assertEquals(LifecycleStatus.DOWN, lifecycleTest.testClass.status)
        dependentComponents.forEach(lifecycleTest::bringDependencyUp)
        assertEquals(LifecycleStatus.DOWN, lifecycleTest.testClass.status)
    }
}
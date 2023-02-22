package net.corda.crypto.persistence.impl.tests

import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.config.impl.CRYPTO_CONNECTION_FACTORY_OBJ
import net.corda.crypto.config.impl.EXPIRE_AFTER_ACCESS_MINS
import net.corda.crypto.config.impl.MAXIMUM_SIZE
import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.crypto.persistence.impl.CryptoConnectionsFactoryImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CryptoConnectionsFactoryTest {

    fun cryptoConnectionsFactoryConfig(expireAfterAccessMins: Int, maximumSize: Int) =
        SmartConfigImpl.empty()
            .withValue("$CRYPTO_CONNECTION_FACTORY_OBJ.$EXPIRE_AFTER_ACCESS_MINS", ConfigValueFactory.fromAnyRef(expireAfterAccessMins))
            .withValue("$CRYPTO_CONNECTION_FACTORY_OBJ.$MAXIMUM_SIZE", ConfigValueFactory.fromAnyRef(maximumSize))

    val lifecycleTest = LifecycleTest {
        addDependency<DbConnectionManager>()
        addDependency<VirtualNodeInfoReadService>()
        addDependency<ConfigurationReadService>()
        CryptoConnectionsFactoryImpl(
            coordinatorFactory,
            configReadService,
            mock(),
            mock(),
            mock()
        )
    }

    @Order(1)
    @Test
    fun `on dependent components UP registers for config updates`() {
        lifecycleTest.run {
            testClass.start()
            bringDependenciesUp()
            verify(configReadService, times(1)).registerComponentForUpdates(any(), any())
            assertEquals(LifecycleStatus.DOWN, testClass.coordinator.status)
        }
    }

    @Order(2)
    @Test
    fun `on receiving config update creates cache and changes status to UP`() {
        lifecycleTest.run {
            assertNull(testClass.connections)
            sendConfigUpdate<CryptoConnectionsFactory>(
                mapOf(
                    BOOT_CONFIG to SmartConfigImpl.empty(),
                    CRYPTO_CONFIG to cryptoConnectionsFactoryConfig(5, 3))
            )
            assertNotNull(testClass.connections)
            assertEquals(LifecycleStatus.UP, testClass.coordinator.status)
        }
    }

    @Order(3)
    @Test
    fun `on receiving RegistrationStatusChangeEvent_ERROR closes resources and changes status to ERROR`() {
         lifecycleTest.run {
            bringDependencyDown<DbConnectionManager>()
             assertEquals(LifecycleStatus.DOWN, testClass.coordinator.status)
             assertNull(testClass.connections)
         }
    }

    @Order(4)
    @Test
    fun `on dependencies coming back UP and receiving config creates cache and changes status to UP`() {
        lifecycleTest.run {
            bringDependencyUp<DbConnectionManager>()
            assertNull(testClass.connections)
            assertEquals(LifecycleStatus.DOWN, testClass.coordinator.status)
            sendConfigUpdate<CryptoConnectionsFactory>(
                mapOf(
                    BOOT_CONFIG to SmartConfigImpl.empty(),
                    CRYPTO_CONFIG to cryptoConnectionsFactoryConfig(5, 3))
            )
            assertNotNull(testClass.connections)
            assertEquals(LifecycleStatus.UP, testClass.coordinator.status)
        }
    }

    @Order(5)
    @Test
    fun `on different cache configuration updates cache`() {
        lifecycleTest.run {
            val previousCache = testClass.connections
            sendConfigUpdate<CryptoConnectionsFactory>(
                mapOf(
                    BOOT_CONFIG to SmartConfigImpl.empty(),
                    CRYPTO_CONFIG to cryptoConnectionsFactoryConfig(5, 4))
            )
            val nextCache = testClass.connections
            assertNotNull(previousCache)
            assertNotNull(nextCache)
            assertNotEquals(previousCache, nextCache)
        }
    }

    @Order(6)
    @Test
    fun `on same cache configuration does not update cache`() {
        lifecycleTest.run {
            val previousCache = testClass.connections
            sendConfigUpdate<CryptoConnectionsFactory>(
                mapOf(
                    BOOT_CONFIG to SmartConfigImpl.empty(),
                    CRYPTO_CONFIG to cryptoConnectionsFactoryConfig(5, 4))
            )
            val nextCache = testClass.connections
            assertNotNull(previousCache)
            assertNotNull(nextCache)
            assertEquals(previousCache, nextCache)
        }
    }
}
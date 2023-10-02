package net.corda.crypto.rest.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.rest.KeyRotationRestResource
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.rest.exception.ServiceUnavailableException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class KeyRotationRestResourceTest {

    private lateinit var publisherFactory: PublisherFactory
    private lateinit var publisher: Publisher
    private lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var lifecycleCoordinator: LifecycleCoordinator
    private val configurationReadService = mock<ConfigurationReadService>()

    @BeforeEach
    fun setup() {
        publisherFactory = mock()
        publisher = mock()
        lifecycleCoordinatorFactory = mock()
        lifecycleCoordinator = mock()

        whenever(publisherFactory.createPublisher(any(), any())).thenReturn(publisher)
    }

    private fun createKeyRotationRestResource(initialise: Boolean = true): KeyRotationRestResource {
        return KeyRotationRestResourceImpl(
            mock(),
            publisherFactory,
            lifecycleCoordinatorFactory,
            configurationReadService
        ).apply { if (initialise) (initialise(SmartConfigImpl.empty())) }
    }

    @Test
    fun `initialize creates the publisher`() {
        createKeyRotationRestResource()
        verify(publisherFactory, times(1)).createPublisher(any(), any())
    }

//    @Test
//    fun `get key rotation status`() {
//        TODO("Not yet implemented")
//    }
//
//    @Test
//    fun `get key rotation status for unknown requestID throws`() {
//        TODO("Not yet implemented")
//    }

    @Test
    fun `start key rotation event triggers successfully`() {
        val keyRotationRestResource = createKeyRotationRestResource()
        keyRotationRestResource.startKeyRotation("", "", false, 0, 0)

        verify(publisher, times(1)).publish(any())
    }

    @Test
    fun `start key rotation event fails when not initialised`() {
        val keyRotationRestResource = createKeyRotationRestResource(false)
        assertThrows<ServiceUnavailableException> {
            keyRotationRestResource.startKeyRotation("", "", false, 0, 0)
        }
        verify(publisher, never()).publish(any())
    }

    @Test
    fun `start event doesnt post up status before being initialised`() {
        val context = getKeyRotationRestResourceTestContext()
        context.run {
            testClass.start()
            context.verifyIsDown<KeyRotationRestResource>()
        }
    }

    @Test
    fun `start event posts up status after all components are up`() {
        val context = getKeyRotationRestResourceTestContext()
        context.run {
            testClass.start()
            bringDependenciesUp()
            context.verifyIsUp<KeyRotationRestResource>()
        }
    }

    private fun getKeyRotationRestResourceTestContext(): LifecycleTest<KeyRotationRestResourceImpl> {
        return LifecycleTest {
            addDependency<LifecycleCoordinatorFactory>()
            addDependency<ConfigurationReadService>()

            KeyRotationRestResourceImpl(
                mock(),
                publisherFactory,
                coordinatorFactory, // This is from test lifecycle class.
                configurationReadService
            )
        }
    }
}

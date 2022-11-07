package net.corda.membership.certificate.service.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.certificates.CertificateUsage
import net.corda.data.certificates.rpc.request.CertificateRpcRequest
import net.corda.data.certificates.rpc.response.CertificateRpcResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.virtualnode.ShortHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CertificatesServiceImplTest {
    private val coordinator = mock<LifecycleCoordinator>()
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    val subscription = mock<RPCSubscription<CertificateRpcRequest, CertificateRpcResponse>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createRPCSubscription(
                any<RPCConfig<CertificateRpcRequest, CertificateRpcResponse>>(),
                any(),
                any()
            )
        } doReturn subscription
    }
    private val configurationReadService = mock<ConfigurationReadService>()
    private val processor = mock<CertificatesProcessor>()

    private val service = CertificatesServiceImpl(
        coordinatorFactory,
        subscriptionFactory,
        configurationReadService,
        processor,
    )

    @Test
    fun `start starts the coordinator`() {
        service.start()

        verify(coordinator).start()
    }

    @Test
    fun `stop stops the coordinator`() {
        service.stop()

        verify(coordinator).stop()
    }

    @Nested
    inner class HandleEventTests {
        @Test
        fun `StartEvent will follow the db connection and the configuration read service`() {
            handler.firstValue.processEvent(StartEvent(), coordinator)

            verify(coordinator).followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                    LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
                )
            )
        }

        @Test
        fun `second StartEvent will stop following the services`() {
            val registration = mock<RegistrationHandle>()
            whenever(coordinator.followStatusChangesByName(any())).doReturn(registration)
            handler.firstValue.processEvent(StartEvent(), coordinator)

            handler.firstValue.processEvent(StartEvent(), coordinator)

            verify(registration).close()
        }

        @Test
        fun `StopEvent will stop everything`() {
            val registrationHandle = mock<RegistrationHandle>()
            whenever(coordinator.followStatusChangesByName(any())).doReturn(registrationHandle)
            handler.firstValue.processEvent(StartEvent(), coordinator)
            val event = ConfigChangedEvent(
                emptySet(),
                mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
            )
            handler.firstValue.processEvent(event, coordinator)
            val configHandle = mock<Resource>()
            whenever(configurationReadService.registerComponentForUpdates(any(), any())).doReturn(configHandle)
            val registrationStatusChangeEvent = RegistrationStatusChangeEvent(
                registrationHandle,
                LifecycleStatus.UP,
            )
            handler.firstValue.processEvent(registrationStatusChangeEvent, coordinator)

            handler.firstValue.processEvent(StopEvent(), coordinator)

            verify(registrationHandle, times(2)).close()
            verify(configHandle).close()
            verify(subscription).close()
        }

        @Test
        fun `second RegistrationStatusChangeEvent will close the handler`() {
            val registrationHandle = mock<RegistrationHandle>()
            whenever(coordinator.followStatusChangesByName(any())).doReturn(registrationHandle)
            handler.firstValue.processEvent(StartEvent(), coordinator)
            val configHandle = mock<Resource>()
            whenever(configurationReadService.registerComponentForUpdates(any(), any())).doReturn(configHandle)
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    registrationHandle,
                    LifecycleStatus.UP,
                ),
                coordinator
            )

            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    registrationHandle,
                    LifecycleStatus.UP,
                ),
                coordinator
            )

            verify(configHandle).close()
        }

        @Test
        fun `RegistrationStatusChangeEvent will wait for configuration`() {
            val registrationHandle = mock<RegistrationHandle>()
            whenever(coordinator.followStatusChangesByName(any())).doReturn(registrationHandle)
            handler.firstValue.processEvent(StartEvent(), coordinator)

            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    registrationHandle,
                    LifecycleStatus.UP,
                ),
                coordinator
            )

            verify(configurationReadService).registerComponentForUpdates(
                coordinator,
                setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)
            )
        }

        @Test
        fun `RegistrationStatusChangeEvent will stop the subscription`() {
            val registrationHandle = mock<RegistrationHandle>()
            whenever(coordinator.followStatusChangesByName(any())).doReturn(registrationHandle)
            handler.firstValue.processEvent(StartEvent(), coordinator)
            val event = ConfigChangedEvent(
                emptySet(),
                mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
            )
            handler.firstValue.processEvent(event, coordinator)

            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    registrationHandle,
                    LifecycleStatus.DOWN,
                ),
                coordinator
            )

            verify(subscription).close()
        }

        @Test
        fun `RegistrationStatusChangeEvent will set the coordinator status to UP`() {
            val registration = mock<RegistrationHandle>()
            whenever(coordinator.followStatusChangesByName(any())).doReturn(registration)
            val event = ConfigChangedEvent(
                emptySet(),
                mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
            )
            handler.firstValue.processEvent(event, coordinator)

            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    registration,
                    LifecycleStatus.UP,
                ),
                coordinator
            )

            verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
        }

        @Test
        fun `RegistrationStatusChangeEvent will set the coordinator status to DOWN`() {
            val registration = mock<RegistrationHandle>()
            whenever(coordinator.followStatusChangesByName(any())).doReturn(registration)
            val event = ConfigChangedEvent(
                emptySet(),
                mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
            )
            handler.firstValue.processEvent(event, coordinator)

            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    registration,
                    LifecycleStatus.DOWN,
                ),
                coordinator
            )

            verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        }

        @Test
        fun `ConfigChangedEvent will subscribe with the correct processor`() {
            val processor = argumentCaptor<RPCResponderProcessor<CertificateRpcRequest, CertificateRpcResponse>>()
            whenever(subscriptionFactory.createRPCSubscription(any(), any(), processor.capture())).doReturn(subscription)
            val event = ConfigChangedEvent(
                emptySet(),
                mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
            )

            handler.firstValue.processEvent(event, coordinator)

            assertThat(processor.firstValue).isInstanceOf(CertificatesProcessor::class.java)
        }

        @Test
        fun `ConfigChangedEvent will start and follow the subscription`() {
            whenever(subscription.subscriptionName).doReturn(mock())
            val event = ConfigChangedEvent(
                emptySet(),
                mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
            )

            handler.firstValue.processEvent(event, coordinator)

            verify(subscription).start()
            verify(coordinator).followStatusChangesByName(setOf(subscription.subscriptionName))
        }

        @Test
        fun `second ConfigChangedEvent will stop the subscription`() {
            val registration = mock<RegistrationHandle>()
            whenever(subscription.subscriptionName).doReturn(mock())
            whenever(coordinator.followStatusChangesByName(setOf(subscription.subscriptionName))).doReturn(registration)
            val event = ConfigChangedEvent(
                emptySet(),
                mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
            )
            handler.firstValue.processEvent(event, coordinator)

            handler.firstValue.processEvent(event, coordinator)

            verify(subscription).close()
            verify(registration).close()
        }
    }

    @Nested
    inner class ClientFunctionalityTest {
        private val hash = ShortHash.of("123456789000")
        private val certificateProcessor = mock<CertificatesProcessor.CertificateProcessor<*>>()

        @Test
        fun `importCertificates import the certificates`() {
            val block = argumentCaptor<(CertificatesProcessor.CertificateProcessor<*>) -> Unit>()
            whenever(
                processor.useCertificateProcessor(
                    eq(hash),
                    eq(CertificateUsage.P2P_SESSION),
                    block.capture()
                )
            ).doReturn(Unit)

            service.importCertificates(
                CertificateUsage.P2P_SESSION,
                hash,
                "alias",
                "certificate",
            )

            block.firstValue.invoke(certificateProcessor)
            verify(certificateProcessor).saveCertificates("alias", "certificate")
        }

        @Test
        fun `retrieveCertificates call the processor`() {
            whenever(certificateProcessor.readCertificates("alias")).doReturn("Certificate")
            val block = argumentCaptor<(CertificatesProcessor.CertificateProcessor<*>) -> String>()
            whenever(
                processor.useCertificateProcessor(
                    eq(hash),
                    eq(CertificateUsage.P2P_SESSION),
                    block.capture(),
                )
            ).doReturn("Certificate")

            service.retrieveCertificates(
                CertificateUsage.P2P_SESSION,
                hash,
                "alias",
            )

            block.firstValue.invoke(certificateProcessor)
            verify(certificateProcessor).readCertificates("alias")
        }

        @Test
        fun `retrieveCertificates return the results`() {
            whenever(
                processor.useCertificateProcessor(
                    eq(hash),
                    eq(CertificateUsage.P2P_SESSION),
                    any<(CertificatesProcessor.CertificateProcessor<*>) -> String>()
                )
            ).doReturn("Certificate")

            val certificates = service.retrieveCertificates(
                CertificateUsage.P2P_SESSION,
                hash,
                "alias",
            )

            assertThat(certificates).isEqualTo("Certificate")
        }

        @Test
        fun `retrieveAllCertificates call the processor`() {
            whenever(certificateProcessor.readAllCertificates()).doReturn(listOf("3"))
            val block = argumentCaptor<(CertificatesProcessor.CertificateProcessor<*>) -> List<String>>()
            whenever(
                processor.useCertificateProcessor(
                    eq(hash),
                    eq(CertificateUsage.P2P_SESSION),
                    block.capture(),
                )
            ).doReturn(listOf("3"))

            service.retrieveAllCertificates(
                CertificateUsage.P2P_SESSION,
                hash,
            )

            block.firstValue.invoke(certificateProcessor)
            verify(certificateProcessor).readAllCertificates()
        }

        @Test
        fun `retrieveAllCertificates return the results`() {
            whenever(
                processor.useCertificateProcessor(
                    eq(hash),
                    eq(CertificateUsage.P2P_SESSION),
                    any<(CertificatesProcessor.CertificateProcessor<*>) -> List<String>>()
                )
            ).doReturn(listOf("1", "2"))

            val certificates = service.retrieveAllCertificates(
                CertificateUsage.P2P_SESSION,
                hash,
            )

            assertThat(certificates).isEqualTo(listOf("1", "2"))
        }
    }
}

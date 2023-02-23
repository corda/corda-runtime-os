package net.corda.membership.certificate.client.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.ShortHash
import net.corda.data.certificates.CertificateUsage
import net.corda.data.certificates.rpc.request.CertificateRpcRequest
import net.corda.data.certificates.rpc.request.ImportCertificateRpcRequest
import net.corda.data.certificates.rpc.request.RetrieveCertificateRpcRequest
import net.corda.data.certificates.rpc.response.CertificateRpcResponse
import net.corda.libs.configuration.SmartConfig
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
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

class CertificatesClientImplTest {
    private val coordinator = mock<LifecycleCoordinator>()
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val sender = mock<RPCSender<CertificateRpcRequest, CertificateRpcResponse>>()
    private val publisher = mock<Publisher> {
        on { publish(any()) } doReturn listOf(CompletableFuture.completedFuture(Unit))
    }
    private val publisherFactory = mock<PublisherFactory> {
        on { createRPCSender(any<RPCConfig<CertificateRpcRequest, CertificateRpcResponse>>(), any()) } doReturn sender
        on { createPublisher(any(), any()) } doReturn publisher
    }
    private val configurationReadService = mock<ConfigurationReadService>()
    private var retrieveCertificates: ((ShortHash?, CertificateUsage, String) -> String?)? = null
    private val mockHostedIdentityEntryFactory = mockConstruction(HostedIdentityEntryFactory::class.java) { _, settings ->
        @Suppress("UNCHECKED_CAST")
        retrieveCertificates = settings.arguments()[5] as? ((ShortHash?, CertificateUsage, String) -> String?)
    }
    private val shortHash = ShortHash.of("AF77BF2471F3")

    private val client = CertificatesClientImpl(
        coordinatorFactory,
        publisherFactory,
        configurationReadService,
        mock(),
        mock(),
        mock(),
        mock(),
        mock(),
        mock(),
        mock(),
        mock(),
    )

    @AfterEach
    fun cleanUp() {
        mockHostedIdentityEntryFactory.close()
    }

    @Nested
    inner class ImportCertificatesTests {
        @Test
        fun `importCertificates sends an ImportCertificateRpcRequest`() {
            whenever(sender.sendRequest(any())).doReturn(mock())
            val event = ConfigChangedEvent(
                emptySet(),
                mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
            )
            handler.firstValue.processEvent(event, coordinator)

            client.importCertificates(CertificateUsage.RPC_API_TLS, null, "alias", "certificate")

            verify(sender)
                .sendRequest(
                    CertificateRpcRequest(
                        CertificateUsage.RPC_API_TLS,
                        null,
                        ImportCertificateRpcRequest(
                            "alias",
                            "certificate"
                        )
                    )
                )
        }

        @Test
        fun `importCertificates throws exception if service had issues`() {
            whenever(sender.sendRequest(any())).doReturn(CompletableFuture.failedFuture(Exception("Failure")))
            val event = ConfigChangedEvent(
                emptySet(),
                mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
            )
            handler.firstValue.processEvent(event, coordinator)

            val exception = assertThrows<Exception> {
                client.importCertificates(CertificateUsage.P2P_TLS, null, "alias", "certificate")
            }

            assertThat(exception).hasMessage("Failure")
        }

        @Test
        fun `importCertificates throws exception if client is not ready`() {
            val exception = assertThrows<Exception> {
                client.importCertificates(CertificateUsage.CODE_SIGNER, null, "alias", "certificate")
            }

            assertThat(exception).hasMessage("Certificates client is not ready")
                .isInstanceOf(IllegalStateException::class.java)
        }
    }

    @Nested
    inner class SetupLocallyHostedIdentityTest {
        @Test
        fun `publishToLocallyHostedIdentities calls createIdentityRecord`() {
            val event = ConfigChangedEvent(
                emptySet(),
                mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
            )
            handler.firstValue.processEvent(event, coordinator)

            client.setupLocallyHostedIdentity(
                shortHash,
                "Alias",
                true,
                "sessionAlias",
                null,
            )

            verify(
                mockHostedIdentityEntryFactory.constructed().first()
            ).createIdentityRecord(
                shortHash,
                "Alias",
                true,
                null,
                "sessionAlias",
            )
        }

        @Test
        fun `hostedIdentityEntryFactory creation send the correct sender`() {
            whenever(sender.sendRequest(any())).doReturn(mock())
            val event = ConfigChangedEvent(
                emptySet(),
                mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
            )
            handler.firstValue.processEvent(event, coordinator)

            retrieveCertificates?.invoke(null, CertificateUsage.P2P_SESSION, "alias")

            verify(sender)
                .sendRequest(
                    CertificateRpcRequest(
                        CertificateUsage.P2P_SESSION,
                        null,
                        RetrieveCertificateRpcRequest(
                            "alias",
                        )
                    )
                )
        }

        @Test
        fun `publishToLocallyHostedIdentities throws exception if publisher is null`() {
            assertThrows<IllegalStateException> {
                client.setupLocallyHostedIdentity(
                    shortHash,
                    "Alias",
                    true,
                    "sessionAlias",
                    null
                )
            }
        }

        @Test
        fun `publishToLocallyHostedIdentities throws exception if publisher future fails`() {
            whenever(publisher.publish(any())).doReturn(listOf(CompletableFuture.failedFuture(CordaRuntimeException(""))))
            val event = ConfigChangedEvent(
                emptySet(),
                mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
            )
            handler.firstValue.processEvent(event, coordinator)

            assertThrows<CordaRuntimeException> {
                client.setupLocallyHostedIdentity(
                    shortHash,
                    "Alias",
                    false,
                    "sessionAlias",
                    null
                )
            }
        }

        @Test
        fun `publishToLocallyHostedIdentities publish the correct record`() {
            val record = mock<Record<String, HostedIdentityEntry>>()
            whenever(
                mockHostedIdentityEntryFactory.constructed().first()
                    .createIdentityRecord(any(), any(), any(), any(), any())
            ).doReturn(record)
            val event = ConfigChangedEvent(
                emptySet(),
                mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
            )
            handler.firstValue.processEvent(event, coordinator)

            client.setupLocallyHostedIdentity(
                shortHash,
                "Alias",
                false,
                "sessionAlias",
                "chain",
            )

            verify(publisher).publish(listOf(record))
        }
    }

    @Nested
    inner class PlumbingTests {
        @Test
        fun `isRunning return true if sender is running`() {
            val event = ConfigChangedEvent(
                emptySet(),
                mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
            )
            handler.firstValue.processEvent(event, coordinator)

            assertThat(client.isRunning).isTrue
        }

        @Test
        fun `isRunning return false if no sender is set`() {
            assertThat(client.isRunning).isFalse
        }

        @Test
        fun `start starts the coordinator`() {
            client.start()

            verify(coordinator).start()
        }

        @Test
        fun `stop stops the coordinator`() {
            client.stop()

            verify(coordinator).stop()
        }

        @Nested
        inner class HandleEventTests {
            @Test
            fun `StartEvent will follow the configuration reader`() {
                handler.firstValue.processEvent(StartEvent(), coordinator)

                verify(coordinator).followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                        LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                        LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
                        LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
                        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                        LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>(),
                        LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
                    )
                )
            }

            @Test
            fun `second StartEvent will stop following the configuration reader`() {
                val registrationHandle = mock<RegistrationHandle>()
                whenever(coordinator.followStatusChangesByName(any())).doReturn(registrationHandle)
                handler.firstValue.processEvent(StartEvent(), coordinator)

                handler.firstValue.processEvent(StartEvent(), coordinator)

                verify(registrationHandle).close()
            }

            @Test
            fun `StopEvent will stop everything`() {
                val registrationHandle = mock<RegistrationHandle>()
                whenever(coordinator.followStatusChangesByName(any())).doReturn(registrationHandle)
                val configHandle = mock<Resource>()
                whenever(configurationReadService.registerComponentForUpdates(any(), any())).doReturn(configHandle)
                handler.firstValue.processEvent(StartEvent(), coordinator)
                val event = ConfigChangedEvent(
                    emptySet(),
                    mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
                )
                handler.firstValue.processEvent(event, coordinator)
                val registrationStatusChangeEvent = RegistrationStatusChangeEvent(
                    registrationHandle,
                    LifecycleStatus.UP,
                )
                handler.firstValue.processEvent(registrationStatusChangeEvent, coordinator)

                handler.firstValue.processEvent(StopEvent(), coordinator)

                verify(sender).close()
                verify(registrationHandle, times(2)).close()
                verify(configHandle).close()
            }

            @Test
            fun `StopEvent will change the status to down`() {
                handler.firstValue.processEvent(StopEvent(), coordinator)

                verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
            }

            @Test
            fun `client starts listen to configuration when configuration is ready`() {
                val registrationHandle = mock<RegistrationHandle>()
                whenever(coordinator.followStatusChangesByName(any())).doReturn(registrationHandle)
                handler.firstValue.processEvent(StartEvent(), coordinator)

                handler.firstValue.processEvent(
                    RegistrationStatusChangeEvent(
                        registrationHandle, LifecycleStatus.UP
                    ),
                    coordinator
                )

                verify(configurationReadService).registerComponentForUpdates(
                    coordinator,
                    setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)
                )
            }

            @Test
            fun `client stop listen to configuration when configuration is ready in the second time`() {
                val configHandle = mock<Resource>()
                whenever(configurationReadService.registerComponentForUpdates(any(), any())).doReturn(configHandle)
                val registrationHandle = mock<RegistrationHandle>()
                whenever(coordinator.followStatusChangesByName(any())).doReturn(registrationHandle)
                handler.firstValue.processEvent(StartEvent(), coordinator)
                handler.firstValue.processEvent(
                    RegistrationStatusChangeEvent(
                        registrationHandle, LifecycleStatus.UP
                    ),
                    coordinator
                )

                handler.firstValue.processEvent(
                    RegistrationStatusChangeEvent(
                        registrationHandle, LifecycleStatus.UP
                    ),
                    coordinator
                )

                verify(configHandle).close()
            }

            @Test
            fun `client set its state to up when sender goes UP`() {
                val registrationHandle = mock<RegistrationHandle>()
                whenever(coordinator.followStatusChangesByName(any())).doReturn(registrationHandle)
                val event = ConfigChangedEvent(
                    emptySet(),
                    mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
                )
                handler.firstValue.processEvent(event, coordinator)

                handler.firstValue.processEvent(
                    RegistrationStatusChangeEvent(
                        registrationHandle, LifecycleStatus.UP
                    ),
                    coordinator
                )

                verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
            }

            @Test
            fun `client goes down and stop listen to configuration when configuration goes down`() {
                val configHandle = mock<Resource>()
                whenever(configurationReadService.registerComponentForUpdates(any(), any())).doReturn(configHandle)
                val registrationHandle = mock<RegistrationHandle>()
                whenever(coordinator.followStatusChangesByName(any())).doReturn(registrationHandle)
                handler.firstValue.processEvent(StartEvent(), coordinator)
                handler.firstValue.processEvent(
                    RegistrationStatusChangeEvent(
                        registrationHandle, LifecycleStatus.UP
                    ),
                    coordinator
                )

                handler.firstValue.processEvent(
                    RegistrationStatusChangeEvent(
                        registrationHandle, LifecycleStatus.DOWN
                    ),
                    coordinator
                )

                verify(configHandle).close()
                verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
            }

            @Test
            fun `client set its state to down when sender goes down`() {
                val registrationHandle = mock<RegistrationHandle>()
                whenever(coordinator.followStatusChangesByName(any())).doReturn(registrationHandle)
                val event = ConfigChangedEvent(
                    emptySet(),
                    mapOf(ConfigKeys.MESSAGING_CONFIG to mock())
                )
                handler.firstValue.processEvent(event, coordinator)

                handler.firstValue.processEvent(
                    RegistrationStatusChangeEvent(
                        registrationHandle, LifecycleStatus.DOWN
                    ),
                    coordinator
                )

                verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
            }

            @Test
            fun `ConfigChangedEvent will create a sender, follow it and start it`() {
                val config = mock<SmartConfig>()
                val name = mock<LifecycleCoordinatorName>()
                whenever(sender.subscriptionName).doReturn(name)

                handler.firstValue.processEvent(
                    ConfigChangedEvent(
                        emptySet(),
                        mapOf(ConfigKeys.MESSAGING_CONFIG to config)
                    ),
                    coordinator
                )

                verify(publisherFactory).createRPCSender(
                    any<RPCConfig<CertificateRpcRequest, CertificateRpcResponse>>(),
                    eq(config)
                )
                verify(sender).start()
                verify(coordinator).followStatusChangesByName(setOf(name))
            }

            @Test
            fun `ConfigChangedEvent will create a publisher start it`() {
                val config = mock<SmartConfig>()
                val name = mock<LifecycleCoordinatorName>()
                whenever(sender.subscriptionName).doReturn(name)

                handler.firstValue.processEvent(
                    ConfigChangedEvent(
                        emptySet(),
                        mapOf(ConfigKeys.MESSAGING_CONFIG to config)
                    ),
                    coordinator
                )

                verify(publisherFactory).createPublisher(
                    any(),
                    eq(config)
                )
                verify(publisher).start()
            }

            @Test
            fun `second ConfigChangedEvent will stop the sender, and stop following it`() {
                val registrationHandle = mock<RegistrationHandle>()
                whenever(coordinator.followStatusChangesByName(any())).doReturn(registrationHandle)
                val config = mock<SmartConfig>()
                handler.firstValue.processEvent(
                    ConfigChangedEvent(
                        emptySet(),
                        mapOf(ConfigKeys.MESSAGING_CONFIG to config)
                    ),
                    coordinator
                )

                handler.firstValue.processEvent(
                    ConfigChangedEvent(
                        emptySet(),
                        mapOf(ConfigKeys.MESSAGING_CONFIG to config)
                    ),
                    coordinator
                )

                verify(sender).close()
                verify(registrationHandle).close()
            }
        }
    }
}

package net.corda.ledger.notary.plugin.factory

import net.corda.ledger.notary.worker.selection.NotaryVirtualNodeSelectorService
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.getMetadataServices
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlowProvider
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryType
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.membership.MemberInfo
import net.corda.v5.membership.NotaryInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PluggableNotaryClientFlowFactoryTest {

    private companion object {
        const val FIRST_NOTARY_SERVICE_PLUGIN_TYPE = "DUMMY"
        const val SECOND_NOTARY_SERVICE_PLUGIN_TYPE = "DUMMY2"

        val FIRST_NOTARY_SERVICE_PARTY = createNotaryService(1)
        val SECOND_NOTARY_SERVICE_PARTY = createNotaryService(2)

        fun createNotaryService(serviceNumber: Int): Party {
            return Party(
                MemberX500Name(
                    "NotaryService$serviceNumber",
                    "R3",
                    "LDN",
                    "GB"
                ),
                mock()
            )
        }
    }

    private lateinit var clientFactory: PluggableNotaryClientFlowFactory
    private lateinit var notaryLookup: NotaryLookup
    private lateinit var virtualNodeSelectorService: NotaryVirtualNodeSelectorService

    @BeforeEach
    fun setup() {
        val mockFirstNotaryServiceInfo = mock<NotaryInfo> {
            on { pluginClass } doReturn FIRST_NOTARY_SERVICE_PLUGIN_TYPE
            on { name } doReturn FIRST_NOTARY_SERVICE_PARTY.name
            on { publicKey } doReturn FIRST_NOTARY_SERVICE_PARTY.owningKey
        }
        val mockSecondNotaryServiceInfo = mock<NotaryInfo> {
            on { pluginClass } doReturn SECOND_NOTARY_SERVICE_PLUGIN_TYPE
            on { name } doReturn SECOND_NOTARY_SERVICE_PARTY.name
            on { publicKey } doReturn SECOND_NOTARY_SERVICE_PARTY.owningKey
        }

        notaryLookup = mock {
            on { notaryServices } doReturn listOf(mockFirstNotaryServiceInfo, mockSecondNotaryServiceInfo)
        }

        val firstServiceVNode = mock<MemberInfo> {
            on { name } doReturn MemberX500Name.parse("CN=VNode1, O=Corda, L=LDN, C=GB")
            on { sessionInitiationKey } doReturn mock()
        }

        val secondServiceVNode = mock<MemberInfo> {
            on { name } doReturn MemberX500Name.parse("CN=VNode2, O=Corda, L=LDN, C=GB")
            on { sessionInitiationKey } doReturn mock()
        }

        virtualNodeSelectorService = mock {
            on { selectVirtualNode(eq(FIRST_NOTARY_SERVICE_PARTY)) } doAnswer  { firstServiceVNode.toParty() }
            on { selectVirtualNode(eq(SECOND_NOTARY_SERVICE_PARTY)) } doAnswer { secondServiceVNode.toParty() }
        }

        clientFactory = PluggableNotaryClientFlowFactory(
            notaryLookup,
            virtualNodeSelectorService
        )
    }

    @Test
    fun `Plugin provider that is not present on the network cannot be loaded`() {
        val exception = assertThrows<IllegalStateException> {
            clientFactory.create(
                FIRST_NOTARY_SERVICE_PARTY,
                mock()
            )
        }

        assertThat(exception.message)
            .contains("Notary flow provider not found for type: $FIRST_NOTARY_SERVICE_PLUGIN_TYPE")
    }

    @Test
    fun `Plugin provider that cannot be instantiated will throw exception`() {
        val providerError = IllegalArgumentException("This is an invalid plugin!")
        // Install a plugin to the factory with type of `DUMMY_TYPE`, but this plugin provider will throw an exception
        // when instantiated
        clientFactory.accept(
            createPluginProviderSandboxContext(
                FirstNotaryServicePluginProvider { _, _ -> throw providerError }
            )
        )
        val exception = assertThrows<CordaRuntimeException> {
            clientFactory.create(
                FIRST_NOTARY_SERVICE_PARTY,
                mock()
            )
        }

        assertThat(exception.message)
            .contains("Exception while trying to create notary client with name: $FIRST_NOTARY_SERVICE_PLUGIN_TYPE")
        assertThat(exception).hasCause(providerError)
    }

    @Test
    fun `Plugin provider that is valid will be instantiated`() {
        // Install a valid plugin provider to the factory with type of `DUMMY_TYPE`
        clientFactory.accept(
            createPluginProviderSandboxContext(
                FirstNotaryServicePluginProvider { _, _ -> mock()}
            )
        )
        assertDoesNotThrow {
            clientFactory.create(
                FIRST_NOTARY_SERVICE_PARTY,
                mock()
            )
        }
    }

    @Test
    fun `Multiple plugin providers that are valid and are for different service will be instantiated`() {
        // Install two valid plugin providers for different types
        clientFactory.accept(
            createPluginProviderSandboxContext(
                FirstNotaryServicePluginProvider { _, _ -> mock()}
            )
        )
        clientFactory.accept(
            createPluginProviderSandboxContext(
                SecondNotaryServicePluginProvider { _, _ -> mock()}
            )
        )

        val firstClient = assertDoesNotThrow {
            clientFactory.create(
                FIRST_NOTARY_SERVICE_PARTY,
                mock()
            )
        }

        val secondClient = assertDoesNotThrow {
            clientFactory.create(
                SECOND_NOTARY_SERVICE_PARTY,
                mock()
            )
        }

        assertThat(firstClient).isNotEqualTo(secondClient)
    }

    @Test
    fun `Plugin provider with no @PluggableNotaryType will not be installed`() {
        clientFactory.accept(createPluginProviderSandboxContext(NoAnnotationProvider()))
        val exception = assertThrows<IllegalStateException> {
            clientFactory.create(
                FIRST_NOTARY_SERVICE_PARTY,
                mock()
            )
        }

        assertThat(exception.message)
            .contains("Notary flow provider not found for type: $FIRST_NOTARY_SERVICE_PLUGIN_TYPE")
    }

    @Test
    fun `Multiple plugin providers cannot be installed for a single type`() {
        val dummyClient = mock<PluggableNotaryClientFlow>()

        clientFactory.accept(
            createPluginProviderSandboxContext(
                FirstNotaryServicePluginProvider { _, _ -> dummyClient }
            )
        )

        // This will be ignored since we already have a registered provider for `DUMMY_PROVIDER`
        clientFactory.accept(
            createPluginProviderSandboxContext(
                FirstNotaryServicePluginProvider { _, _ -> mock() }
            )
        )

        val createdClient = assertDoesNotThrow {
            clientFactory.create(
                FIRST_NOTARY_SERVICE_PARTY,
                mock()
            )
        }

        assertThat(createdClient).isEqualTo(dummyClient)
    }

    private fun createPluginProviderSandboxContext(
        provider: PluggableNotaryClientFlowProvider
    ): MutableSandboxGroupContext {
        return mock {
            on { getMetadataServices<PluggableNotaryClientFlowProvider>() } doReturn setOf(provider)
        }
    }

    @PluggableNotaryType(FIRST_NOTARY_SERVICE_PLUGIN_TYPE)
    private class FirstNotaryServicePluginProvider(
        private val createLogic: (notary: Party, stx: UtxoSignedTransaction) -> PluggableNotaryClientFlow
    ) : PluggableNotaryClientFlowProvider {
        override fun create(
            notary: Party,
            stx: UtxoSignedTransaction
        ): PluggableNotaryClientFlow = createLogic(notary, stx)
    }

    @PluggableNotaryType(SECOND_NOTARY_SERVICE_PLUGIN_TYPE)
    private class SecondNotaryServicePluginProvider(
        private val createLogic: (notary: Party, stx: UtxoSignedTransaction) -> PluggableNotaryClientFlow
    ) : PluggableNotaryClientFlowProvider {
        override fun create(
            notary: Party,
            stx: UtxoSignedTransaction
        ): PluggableNotaryClientFlow = createLogic(notary, stx)
    }

    private class NoAnnotationProvider : PluggableNotaryClientFlowProvider {
        override fun create(notary: Party, stx: UtxoSignedTransaction): PluggableNotaryClientFlow = mock()
    }

    private fun MemberInfo.toParty(): Party = Party(name, sessionInitiationKey)
}
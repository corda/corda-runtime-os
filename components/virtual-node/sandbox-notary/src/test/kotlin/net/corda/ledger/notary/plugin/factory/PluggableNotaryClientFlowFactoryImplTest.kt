package net.corda.ledger.notary.plugin.factory

import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.getMetadataServices
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlowProvider
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryType
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey

class PluggableNotaryClientFlowFactoryImplTest {

    private companion object {
        const val DUMMY_TYPE = "DUMMY"
        val DUMMY_NOTARY_KEY = mock<PublicKey>()
        val DUMMY_NOTARY_NAME = MemberX500Name("Alice", "Alice Corp", "LDN", "GB")

        val DUMMY_NOTARY_PARTY = Party(
            DUMMY_NOTARY_NAME,
            DUMMY_NOTARY_KEY
        )
    }

    private lateinit var clientFactory: PluggableNotaryClientFlowFactoryImpl

    @BeforeEach
    fun setup() {
        clientFactory = PluggableNotaryClientFlowFactoryImpl()
    }

    @Test
    fun `Plugin provider that is not present on the network cannot be loaded`() {
        val exception = assertThrows<IllegalStateException> {
            clientFactory.create(
                DUMMY_NOTARY_PARTY,
                DUMMY_TYPE,
                mock()
            )
        }

        assertThat(exception.message).contains("Notary flow provider not found for type: $DUMMY_TYPE")
    }

    @Test
    fun `Plugin provider that cannot be instantiated will throw exception`() {
        val providerError = IllegalArgumentException("This is an invalid plugin!")
        // Install a plugin to the factory with type of `DUMMY_TYPE`, but this plugin provider will throw an exception
        // when instantiated
        clientFactory.accept(
            createPluginProviderSandboxContext(
                PluginProvider { _, _ -> throw providerError }
            )
        )
        val exception = assertThrows<CordaRuntimeException> {
            clientFactory.create(
                DUMMY_NOTARY_PARTY,
                DUMMY_TYPE,
                mock()
            )
        }

        assertThat(exception.message).contains("Exception while trying to create notary client with name: $DUMMY_TYPE")
        assertThat(exception).hasCause(providerError)
    }

    @Test
    fun `Plugin provider that is valid will be instantiated`() {
        // Install a valid plugin provider to the factory with type of `DUMMY_TYPE`
        clientFactory.accept(
            createPluginProviderSandboxContext(
                PluginProvider { _, _ -> mock()}
            )
        )
        clientFactory.create(
            DUMMY_NOTARY_PARTY,
            DUMMY_TYPE,
            mock()
        )
    }

    @Test
    fun `Plugin provider with no @PluggableNotaryType will not be installed`() {
        clientFactory.accept(createPluginProviderSandboxContext(NoAnnotationProvider()))
        val exception = assertThrows<IllegalStateException> {
            clientFactory.create(
                DUMMY_NOTARY_PARTY,
                DUMMY_TYPE,
                mock()
            )
        }

        assertThat(exception.message).contains("Notary flow provider not found for type: $DUMMY_TYPE")
    }

    @Test
    fun `Multiple plugin providers cannot be installed for a single type`() {
        val dummyClient = mock<PluggableNotaryClientFlow>()

        clientFactory.accept(
            createPluginProviderSandboxContext(
                PluginProvider { _, _ -> dummyClient }
            )
        )

        // This will be ignored since we already have a registered provider for `DUMMY_PROVIDER`
        clientFactory.accept(
            createPluginProviderSandboxContext(
                PluginProvider { _, _ -> mock() }
            )
        )

        val createdClient = clientFactory.create(
            DUMMY_NOTARY_PARTY,
            DUMMY_TYPE,
            mock()
        )

        assertThat(createdClient).isEqualTo(dummyClient)
    }

    private fun createPluginProviderSandboxContext(
        provider: PluggableNotaryClientFlowProvider
    ): MutableSandboxGroupContext {
        return mock {
            on { getMetadataServices<PluggableNotaryClientFlowProvider>() } doReturn setOf(provider)
        }
    }

    @PluggableNotaryType(DUMMY_TYPE)
    private class PluginProvider(
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
}
package net.corda.sandboxgroupcontext.test

import java.lang.invoke.MethodHandles
import java.nio.file.Path
import java.security.KeyPairGenerator
import kotlin.reflect.full.primaryConstructor
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.core.parseSecureHash
import net.corda.internal.serialization.amqp.api.SerializationServiceInternal
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.ledger.utxo.data.state.EncumbranceGroupImpl
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.sandboxgroupcontext.RequireSandboxAMQP
import net.corda.sandboxgroupcontext.RequireSandboxAMQP.AMQP_SERIALIZATION_SERVICE
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.KeySchemeCodes
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
@RequireSandboxAMQP
class AMQPSerializationTest {
    private companion object {
        private const val CONTRACT_CPB = "META-INF/sandbox-contract-cpk.cpb"
        private const val STATE_CLASS_NAME = "com.example.contract.ExampleState"
        private const val TIMEOUT_MILLIS = 30000L

        private const val SCHEME_NAME = KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
        private const val ENCUMBRANCE_GROUP_TAG = "encumbrance-tag"
        private const val AMOUNT = 1234999L
        private const val LABEL = "my-tag"
        private const val REF_IDX = 99

        private val NOTARY_X500_NAME = MemberX500Name.parse("O=NotaryService, L=London, C=GB")
        private val TXN_ID = parseSecureHash("SHA-256:CDFF8A944383063AB86AFE61488208CCCC84149911F85BE4F0CACCF399CA9903")
    }

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var cipherSchemeMetadata: CipherSchemeMetadata

    @Suppress("JUnitMalformedDeclaration")
    @RegisterExtension
    private val lifecycle = AllTestsLifecycle()

    private lateinit var virtualNode: VirtualNodeService

    @Suppress("SameParameterValue")
    private fun createKeyPairGenerator(schemeName: String): KeyPairGenerator {
        val keyScheme = cipherSchemeMetadata.findKeyScheme(schemeName)
        return KeyPairGenerator.getInstance(
            keyScheme.algorithmName,
            cipherSchemeMetadata.providers[keyScheme.providerName]
        ).apply {
            keyScheme.algSpec?.also { algorithmSpec ->
                initialize(algorithmSpec, cipherSchemeMetadata.secureRandom)
            } ?: run {
                keyScheme.keySize?.also { keySize ->
                    initialize(keySize, cipherSchemeMetadata.secureRandom)
                }
            }
        }
    }

    private fun SandboxGroupContext.getSerializationService(): SerializationServiceInternal {
         return getObjectByKey<SerializationServiceInternal>(AMQP_SERIALIZATION_SERVICE)
            ?: fail("No AMQP serialization service found")
    }

    private fun <T : ContractState> createState(contractStateClass: Class<T>, vararg params: Any?): T {
        return contractStateClass.kotlin.primaryConstructor?.call(*params)
            ?: fail("Contract state $STATE_CLASS_NAME has no primary constructor")
    }

    @Suppress("unchecked_cast")
    private fun <T> fieldValue(value: Any, fieldName: String, fieldType: Class<T>): T {
        val lookup = MethodHandles.privateLookupIn(value::class.java, MethodHandles.lookup())
        return lookup.findGetter(value::class.java, fieldName, fieldType).invoke(value) as T
    }

    private inline fun <reified T> fieldValue(value: Any, fieldName: String): T {
        return fieldValue(value, fieldName, T::class.java)
    }

    @BeforeAll
    fun setup(
        @InjectService(timeout = TIMEOUT_MILLIS)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            virtualNode = setup.fetchService(TIMEOUT_MILLIS)
        }
    }

    @ParameterizedTest
    @EnumSource(SandboxGroupType::class)
    @DisplayName("Serialize & Deserialize ContractState: {0}")
    fun testSerializeAndDeserializeContractState(sandboxGroupType: SandboxGroupType) {
        val keyPair = createKeyPairGenerator(SCHEME_NAME).genKeyPair()
        var serializedBytes: SerializedBytes<ContractState>? = null

        // Create a contract state, and then serialise it.
        virtualNode.withSandbox(CONTRACT_CPB, sandboxGroupType) { vns, ctx ->
            val serializationService = ctx.getSerializationService()
            val contractStateClass = vns.getContractStateClass(ctx, STATE_CLASS_NAME)
            val contractState = createState(contractStateClass, keyPair.public, AMOUNT, LABEL)
            serializedBytes = serializationService.serialize(contractState)
        }

        // Deserialize the contract state inside a new sandbox.
        virtualNode.withSandbox(CONTRACT_CPB, sandboxGroupType) { vns, ctx ->
            val serializationService = ctx.getSerializationService()
            val contractStateClass = vns.getContractStateClass(ctx, STATE_CLASS_NAME)
            @Suppress("unchecked_cast")
            val contractState = serializationService.deserialize(
                serializedBytes ?: fail("No bytes to deserialize!"),
                contractStateClass as Class<ContractState>
            )

            assertEquals(STATE_CLASS_NAME, contractState::class.java.name)
            assertEquals(keyPair.public, contractState.participants.single())
            assertEquals(AMOUNT, fieldValue(contractState, "amount", java.lang.Long.TYPE))
            assertEquals(LABEL, fieldValue<String>(contractState, "tag"))
        }
    }

    @ParameterizedTest
    @EnumSource(SandboxGroupType::class)
    @DisplayName("Serialize & Deserialize StateAndRef: {0}")
    fun testSerializeAndDeserializeStateAndRef(sandboxGroupType: SandboxGroupType) {
        val keyPairGenerator = createKeyPairGenerator(SCHEME_NAME)
        val notaryKeyPair = keyPairGenerator.genKeyPair()
        val keyPair = keyPairGenerator.genKeyPair()
        var serializedBytes: SerializedBytes<Bucket<ContractState>>? = null

        // Create a StateAndRef, and then serialise it.
        virtualNode.withSandbox(CONTRACT_CPB, sandboxGroupType) { vns, ctx ->
            val serializationService = ctx.getSerializationService()
            val contractStateClass = vns.getContractStateClass(ctx, STATE_CLASS_NAME)
            val contractState = createState(contractStateClass, keyPair.public, AMOUNT, LABEL)
            val transactionState = TransactionStateImpl(
                contractState,
                notaryName = NOTARY_X500_NAME,
                notaryKey = notaryKeyPair.public,
                EncumbranceGroupImpl(0, ENCUMBRANCE_GROUP_TAG)
            )
            val stateAndRef = StateAndRefImpl(transactionState, StateRef(TXN_ID, REF_IDX))
            serializedBytes = serializationService.serialize(Bucket(stateAndRef))
        }

        // Deserialize the StateAndRef inside a new sandbox.
        virtualNode.withSandbox(CONTRACT_CPB, sandboxGroupType) { _, ctx ->
            val serializationService = ctx.getSerializationService()
            @Suppress("unchecked_cast")
            val stateAndRef = serializationService.deserialize(
                serializedBytes ?: fail("No bytes to deserialize!"),
                Bucket::class.java as Class<Bucket<ContractState>>
            ).stateAndRef

            assertEquals(TXN_ID, stateAndRef.ref.transactionId)
            assertEquals(REF_IDX, stateAndRef.ref.index)
            assertEquals(NOTARY_X500_NAME, stateAndRef.state.notaryName)
            assertEquals(notaryKeyPair.public, stateAndRef.state.notaryKey)

            stateAndRef.state.contractState.also { contractState ->
                assertEquals(keyPair.public, contractState.participants.single())
                assertEquals(AMOUNT, fieldValue(contractState, "amount", java.lang.Long.TYPE))
                assertEquals(LABEL, fieldValue<String>(contractState, "tag"))
                assertEquals(STATE_CLASS_NAME, contractState::class.java.name)
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SandboxGroupType::class)
    @DisplayName("Serialize & Deserialize WireTransaction: {0}")
    fun testSerializeAndDeserializeWireTransaction(sandboxGroupType: SandboxGroupType) {
        var origWireTx: WireTransaction? = null
        var serializedBytes: SerializedBytes<WireTransaction>? = null

        // Create a WireTransaction, and then serialise it.
        virtualNode.withSandbox(CONTRACT_CPB, sandboxGroupType) { _, ctx ->
            val serializationService = ctx.getSerializationService()

            origWireTx = getWireTransactionExample(
                digestService = ctx.getSandboxSingletonService(),
                merkleTreeProvider = ctx.getSandboxSingletonService(),
                jsonMarshallingService = ctx.getSandboxSingletonService(),
                jsonValidator = ctx.getSandboxSingletonService()
            )

            serializedBytes = serializationService.serialize(origWireTx!!)
        }

        // Deserialize the WireTransaction inside a new sandbox.
        virtualNode.withSandbox(CONTRACT_CPB, sandboxGroupType) { _, ctx ->
            val serializationService = ctx.getSerializationService()
            val wireTx = serializationService.deserialize(
                serializedBytes ?: fail("No bytes to deserialize!"),
                WireTransaction::class.java
            )

            assertEquals(origWireTx, wireTx)
        }
    }

    @ParameterizedTest
    @EnumSource(SandboxGroupType::class)
    @DisplayName("Serialize & Deserialize with snappy compression: {0}")
    fun `serialize and deserialize uses snappy compression by default`(sandboxGroupType: SandboxGroupType) {
        virtualNode.withSandbox(CONTRACT_CPB, sandboxGroupType) { _, ctx ->
            val serializationService = ctx.getSerializationService()

            val transaction = getWireTransactionExample(
                digestService = ctx.getSandboxSingletonService(),
                merkleTreeProvider = ctx.getSandboxSingletonService(),
                jsonMarshallingService = ctx.getSandboxSingletonService(),
                jsonValidator = ctx.getSandboxSingletonService()
            )

            val serializedBytes = serializationService.serialize(transaction)
            assertThat(serializedBytes.bytes.toString(Charsets.UTF_8)).contains("sNaPpY")
            val deserialized = serializationService.deserialize(serializedBytes, WireTransaction::class.java)
            assertThat(transaction).isEqualTo(deserialized)
        }
    }

    @ParameterizedTest
    @EnumSource(SandboxGroupType::class)
    @DisplayName("Serialize & Deserialize with snappy compression: {0}")
    fun `serialize and deserialize without compression`(sandboxGroupType: SandboxGroupType) {
        virtualNode.withSandbox(CONTRACT_CPB, sandboxGroupType) { _, ctx ->
            val serializationService = ctx.getSerializationService()

            val transaction = getWireTransactionExample(
                digestService = ctx.getSandboxSingletonService(),
                merkleTreeProvider = ctx.getSandboxSingletonService(),
                jsonMarshallingService = ctx.getSandboxSingletonService(),
                jsonValidator = ctx.getSandboxSingletonService()
            )

            val serializedBytes = serializationService.serialize(transaction, withCompression = false)
            assertThat(serializedBytes.bytes.toString(Charsets.UTF_8)).doesNotContain("sNaPpY")
            val deserialized = serializationService.deserialize(serializedBytes, WireTransaction::class.java)
            assertThat(transaction).isEqualTo(deserialized)
        }
    }

    @CordaSerializable
    data class Bucket<T : ContractState>(val stateAndRef: StateAndRef<T>)
}

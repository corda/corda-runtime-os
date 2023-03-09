package net.corda.simulator.runtime.ledger.utxo

import net.corda.simulator.factories.SimulatorConfigurationBuilder
import net.corda.simulator.runtime.notary.SimTimeWindow
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.simulator.runtime.testutils.generateKey
import net.corda.simulator.runtime.testutils.generateKeys
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Instant
import kotlin.time.Duration.Companion.days

class UtxoFilteredTransactionBuilderTest {

    private val notaryX500 = MemberX500Name.parse("O=Notary,L=London,C=GB")
    private val config = SimulatorConfigurationBuilder.create().build()
    private val publicKeys = generateKeys(2)
    private val notary = Party(notaryX500, generateKey())
    private val timeWindow = SimTimeWindow(Instant.now(), Instant.now().plusMillis(1.days.inWholeMilliseconds))
    private lateinit var signedTx : UtxoSignedTransactionBase
    private val command = TestUtxoCommand()

    @BeforeEach
    fun `create signed tx`(){
        val persistenceService = mock<PersistenceService>()
        val serializationService = BaseSerializationService()
        val signingService = mock<SigningService>()
        signedTx = UtxoSignedTransactionBase(
            publicKeys.map { toSignatureWithMetadata(it, Instant.now()) },
            UtxoStateLedgerInfo(
                listOf(command),
                emptyList(),
                notary,
                emptyList(),
                publicKeys,
                timeWindow,
                listOf(
                    ContractStateAndEncumbranceTag(
                        TestUtxoState("State1", publicKeys), ""),
                    ContractStateAndEncumbranceTag(
                        TestUtxoState("State2", publicKeys), "")
                ),
                emptyList()
            ),
            signingService,
            serializationService,
            persistenceService,
            config
        )
    }

    @Test
    fun `should always have metadata and id`() {
        val filteredTxBuilder = UtxoFilteredTransactionBuilderBase(signedTx)
        val filteredTx = filteredTxBuilder.build()

        assertThat(filteredTx.id, `is`(signedTx.id))
        assertNotNull(filteredTx.metadata)
    }

    @Test
    fun `should be able to filter out notary and time window`() {
        val filteredTxBuilder = UtxoFilteredTransactionBuilderBase(signedTx)
        val filteredTx = filteredTxBuilder.build()

        assertNull(filteredTx.notary)
        assertNull(filteredTx.timeWindow)
    }

    @Test
    fun `should be able to fetch notary and time-window`() {
        val filteredTxBuilder = UtxoFilteredTransactionBuilderBase(signedTx)
        val filteredTx = filteredTxBuilder.withNotary().withTimeWindow().build()

        assertThat(filteredTx.notary, `is`(signedTx.notary))
        assertThat(filteredTx.timeWindow, `is`(signedTx.timeWindow))
    }

    @Test
    fun `should be able to fetch notary and not time-window and the other way around`() {
        val filteredTxBuilder = UtxoFilteredTransactionBuilderBase(signedTx)
        val filteredTx = filteredTxBuilder.withNotary().build()

        assertThat(filteredTx.notary, `is`(signedTx.notary))
        assertNull(filteredTx.timeWindow)

        val anotherFilteredTxBuilder = UtxoFilteredTransactionBuilderBase(signedTx)
        val anotherFilteredTx = anotherFilteredTxBuilder.withTimeWindow().build()

        assertThat(anotherFilteredTx.timeWindow, `is`(signedTx.timeWindow))
        assertNull(anotherFilteredTx.notary)
    }

    @Test
    fun `should be able to filter out components`(){
        val filteredTxBuilder = UtxoFilteredTransactionBuilderBase(signedTx)
        val filteredTx = filteredTxBuilder.withNotary().withTimeWindow().build()

        assertThat(filteredTx.commands, instanceOf(UtxoFilteredData.Removed::class.java))
        assertThat(filteredTx.referenceStateRefs, instanceOf(UtxoFilteredData.Removed::class.java))
        assertThat(filteredTx.inputStateRefs, instanceOf(UtxoFilteredData.Removed::class.java))
        assertThat(filteredTx.signatories, instanceOf(UtxoFilteredData.Removed::class.java))
        assertThat(filteredTx.outputStateAndRefs, instanceOf(UtxoFilteredData.Removed::class.java))
    }

    @Test
    fun `should be able to get number of commands with no data`(){
        val filteredTxBuilder = UtxoFilteredTransactionBuilderBase(signedTx)
        val filteredTx = filteredTxBuilder.withCommandsSize().build()

        assertThat(filteredTx.commands, instanceOf(UtxoFilteredData.SizeOnly::class.java))
        val commands = filteredTx.commands as UtxoFilteredData.SizeOnly
        assertThat(commands.size, `is`(1))
    }

    @Test
    fun `should be able to get command data`(){
        val filteredTxBuilder = UtxoFilteredTransactionBuilderBase(signedTx)
        val filteredTx = filteredTxBuilder.withCommands().build()

        assertThat(filteredTx.commands, instanceOf(UtxoFilteredData.Audit::class.java))
        val commands = filteredTx.commands as UtxoFilteredData.Audit<Command>
        assertThat(commands.size, `is`(1))
        assertThat(commands.values.keys.size, `is`(1))
        assertThat(commands.values[0], instanceOf(TestUtxoCommand::class.java))
    }
}
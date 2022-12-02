package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.common.json.validation.impl.JsonValidatorImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.impl.transaction.filtered.factory.FilteredTransactionFactoryImpl
import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredTransaction
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class UtxoFilteredTransactionTest {

    private companion object {
        val STATE_REF_1 = "state ref 1".toByteArray()
        val STATE_REF_2 = "state ref 2".toByteArray()
        val STATE_REF_3 = "state ref 3".toByteArray()
        val OUTPUT_STATE_1 = "output state 1".toByteArray()
        val OUTPUT_INFO_1 = "output info 1".toByteArray()
        val OUTPUT_STATE_2 = "output state 2".toByteArray()
        val OUTPUT_INFO_2 = "output info 2".toByteArray()
        val NOTARY = "notary".toByteArray()
        val TIME_WINDOW = "time window".toByteArray()
        val COMMAND = "command".toByteArray()
        val COMMAND_INFO = "command_info".toByteArray()

        val inputId = SecureHash.parse("SHA-256:1234567890")
        val notary = Party(MemberX500Name.Companion.parse("O=notary, L=London, C=GB"), mock())
        val timeWindow = mock<TimeWindow>()

        val outputInfo1 = UtxoOutputInfoComponent(null, notary, "", "")
        val outputInfo2 = UtxoOutputInfoComponent(3, notary, "", "")
    }

    private lateinit var wireTransaction: WireTransaction
    private lateinit var filteredTransaction: FilteredTransaction

    private val digestService =
        DigestServiceImpl(PlatformDigestServiceImpl(CipherSchemeMetadataImpl()), null)
    private val jsonMarshallingService = JsonMarshallingServiceImpl()
    private val jsonValidator = JsonValidatorImpl()
    private val merkleTreeProvider = MerkleTreeProviderImpl(digestService)
    private val serializationService = mock<SerializationService>()

    private val filteredTransactionFactory = FilteredTransactionFactoryImpl(
        jsonMarshallingService,
        merkleTreeProvider,
        serializationService
    )

    @BeforeEach
    fun beforeEach() {
        whenever(serializationService.deserialize(NOTARY, Party::class.java)).thenReturn(notary)
        whenever(serializationService.deserialize(NOTARY, Any::class.java)).thenReturn(notary)
        whenever(serializationService.deserialize(TIME_WINDOW, TimeWindow::class.java)).thenReturn(timeWindow)
        whenever(serializationService.deserialize(TIME_WINDOW, Any::class.java)).thenReturn(timeWindow)
        whenever(serializationService.deserialize(OUTPUT_INFO_1, UtxoOutputInfoComponent::class.java)).thenReturn(
            outputInfo1
        )
        whenever(serializationService.deserialize(OUTPUT_INFO_2, UtxoOutputInfoComponent::class.java)).thenReturn(
            outputInfo2
        )
        whenever(serializationService.deserialize(COMMAND_INFO, Any::class.java)).thenReturn(listOf(""))
        whenever(serializationService.deserialize(STATE_REF_1, StateRef::class.java)).thenReturn(StateRef(inputId, 0))
        whenever(serializationService.deserialize(STATE_REF_2, StateRef::class.java)).thenReturn(StateRef(inputId, 5))
        whenever(serializationService.deserialize(STATE_REF_3, StateRef::class.java)).thenReturn(StateRef(inputId, 3))
        whenever(serializationService.deserialize(OUTPUT_STATE_1, ContractState::class.java)).thenReturn(OutputState1())
        whenever(serializationService.deserialize(OUTPUT_STATE_2, ContractState::class.java)).thenReturn(OutputState2())
        whenever(serializationService.deserialize(COMMAND, Any::class.java)).thenReturn(mock<Command>())

        wireTransaction = getWireTransactionExample(
            digestService,
            merkleTreeProvider,
            jsonMarshallingService,
            jsonValidator,
            componentGroupLists = listOf(
                listOf(NOTARY, TIME_WINDOW),
                listOf(OUTPUT_INFO_1, OUTPUT_INFO_2),
                listOf(COMMAND_INFO),
                emptyList(),
                listOf(STATE_REF_1, STATE_REF_2),
                listOf(STATE_REF_3),
                listOf(OUTPUT_STATE_1, OUTPUT_STATE_2),
                listOf(COMMAND)
            )
        )

        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.METADATA.ordinal,
                    TransactionMetadataImpl::class.java
                ),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.NOTARY.ordinal, Any::class.java),
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                    UtxoOutputInfoComponent::class.java
                ),
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS_INFO.ordinal),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.INPUTS.ordinal, StateRef::class.java),
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS.ordinal,
                    ContractState::class.java
                ),
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS.ordinal),
            )
        ) { true }

    }

    @Test
    fun `metada and id are always present`() {
        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.METADATA.ordinal,
                    TransactionMetadataImpl::class.java
                )
            )
        ) { true }

        val utxoFilteredTx: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTx.id).isEqualTo(wireTransaction.id)
        Assertions.assertThat(utxoFilteredTx.metadata.getLedgerModel())
            .isEqualTo(wireTransaction.metadata.getLedgerModel())

    }

    @Test
    fun `Can reconstruct filtered output`() {
        Assertions.assertThat(filteredTransaction.getComponentGroupContent(UtxoComponentGroup.OUTPUTS_INFO.ordinal))
            .hasSize(2)
        Assertions.assertThat(filteredTransaction.getComponentGroupContent(UtxoComponentGroup.OUTPUTS.ordinal))
            .hasSize(2)

        val utxoFilteredTx: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTx.outputStateAndRefs)
            .isInstanceOf(UtxoFilteredData.UtxoFilteredDataAudit::class.java)
        val outputs = utxoFilteredTx.outputStateAndRefs as UtxoFilteredData.UtxoFilteredDataAudit<StateAndRef<*>>

        Assertions.assertThat(outputs.size).isEqualTo(2)
        Assertions.assertThat(outputs.values.size).isEqualTo(2)
        Assertions.assertThat(outputs.values[0]?.state?.contractState).isInstanceOf(OutputState1::class.java)
        Assertions.assertThat(outputs.values[1]?.state?.contractState).isInstanceOf(OutputState2::class.java)
    }

    @Test
    fun `can fetch input state refs`() {
        val utxoFilteredTx: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTx.inputStateRefs)
            .isInstanceOf(UtxoFilteredData.UtxoFilteredDataAudit::class.java)
        val inputStateRefs = utxoFilteredTx.inputStateRefs as UtxoFilteredData.UtxoFilteredDataAudit<StateRef>
        Assertions.assertThat(inputStateRefs.size).isEqualTo(2)
        Assertions.assertThat(inputStateRefs.values.keys.first()).isEqualTo(0)
        Assertions.assertThat(inputStateRefs.values[0]?.transactionHash).isEqualTo(inputId)
        Assertions.assertThat(inputStateRefs.values[1]?.transactionHash).isEqualTo(inputId)
        Assertions.assertThat(inputStateRefs.values[0]?.index).isEqualTo(0)
        Assertions.assertThat(inputStateRefs.values[1]?.index).isEqualTo(5)
    }

    @Test
    fun `can filter input state refs`() {
        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.METADATA.ordinal,
                    TransactionMetadataImpl::class.java
                ),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.NOTARY.ordinal, Any::class.java),
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                    UtxoOutputInfoComponent::class.java
                ),
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS_INFO.ordinal),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.INPUTS.ordinal, StateRef::class.java),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.REFERENCES.ordinal, StateRef::class.java),
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS.ordinal,
                    ContractState::class.java
                ),
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS.ordinal),
            )
        ) {
            when (it) {
                is StateRef -> if (it.index == 0) false else true
                else -> true
            }
        }

        Assertions.assertThat(filteredTransaction.getComponentGroupContent(UtxoComponentGroup.INPUTS.ordinal))
            .hasSize(1)

        val utxoFilteredTx: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTx.inputStateRefs)
            .isInstanceOf(UtxoFilteredData.UtxoFilteredDataAudit::class.java)
        val inputStateRefs = utxoFilteredTx.inputStateRefs as UtxoFilteredData.UtxoFilteredDataAudit<StateRef>
        Assertions.assertThat(inputStateRefs.size).isEqualTo(2)
        Assertions.assertThat(inputStateRefs.values.size).isEqualTo(1)
        Assertions.assertThat(inputStateRefs.values.keys.first()).isEqualTo(1)
        Assertions.assertThat(inputStateRefs.values[1]?.transactionHash).isEqualTo(inputId)
        Assertions.assertThat(inputStateRefs.values[1]?.index).isEqualTo(5)
    }


    @Test
    fun `can get notary and time window`() {
        val utxoFilteredTransaction: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTransaction.notary).isEqualTo(notary)
        Assertions.assertThat(utxoFilteredTransaction.timeWindow).isEqualTo(timeWindow)
    }

    @Test
    fun `can filter out notary but not time window`() {
        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.METADATA.ordinal,
                    TransactionMetadataImpl::class.java
                ),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.NOTARY.ordinal, Any::class.java),
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                    UtxoOutputInfoComponent::class.java
                ),
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS_INFO.ordinal),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.INPUTS.ordinal, StateRef::class.java),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.REFERENCES.ordinal, StateRef::class.java),
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS.ordinal,
                    ContractState::class.java
                ),
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS.ordinal),
            )
        ) {
            when (it) {
                is Party -> false
                else -> true
            }
        }
        val utxoFilteredTransaction: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTransaction.notary).isNull()
        Assertions.assertThat(utxoFilteredTransaction.timeWindow).isEqualTo(timeWindow)
    }

    @Test
    fun `can filter out notary and time window`() {
        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.METADATA.ordinal,
                    TransactionMetadataImpl::class.java
                ),
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                    UtxoOutputInfoComponent::class.java
                ),
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS_INFO.ordinal),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.INPUTS.ordinal, StateRef::class.java),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.REFERENCES.ordinal, StateRef::class.java),
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS.ordinal,
                    ContractState::class.java
                ),
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS.ordinal),
            )
        ) {
            when (it) {
                is Party -> false
                else -> true
            }
        }
        val utxoFilteredTransaction: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTransaction.notary).isNull()
        Assertions.assertThat(utxoFilteredTransaction.timeWindow).isNull()
    }

    @Test
    fun `can get the number of commmands but no data`() {
        val utxoFilteredTransaction: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTransaction.commands)
            .isInstanceOf(UtxoFilteredData.UtxoFilteredDataSizeOnly::class.java)
        val commands = utxoFilteredTransaction.commands as UtxoFilteredData.UtxoFilteredDataSizeOnly
        Assertions.assertThat(commands.size).isEqualTo(1)
    }

    @Test
    fun `cannot find out about reference states`() {
        val utxoFilteredTransaction: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTransaction.referenceInputStateRefs)
            .isInstanceOf(UtxoFilteredData.UtxoFilteredDataRemoved::class.java)

    }
}

class OutputState1 : ContractState {
    override val participants: List<PublicKey>
        get() = TODO("Not yet implemented")
}

class OutputState2 : ContractState {
    override val participants: List<PublicKey>
        get() = TODO("Not yet implemented")
}

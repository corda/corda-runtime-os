package net.corda.ledger.persistence.utxo.tests

import net.corda.common.json.validation.JsonValidator
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.core.SecureHashImpl
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedGroupParameters
import net.corda.db.persistence.testkit.components.VirtualNodeService
import net.corda.db.testkit.DbUtils
import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.TransactionStatus.DRAFT
import net.corda.ledger.common.data.transaction.TransactionStatus.INVALID
import net.corda.ledger.common.data.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.common.data.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.data.transaction.filtered.ComponentGroupFilterParameters
import net.corda.ledger.common.data.transaction.filtered.FilteredTransaction
import net.corda.ledger.common.data.transaction.filtered.factory.FilteredTransactionFactory
import net.corda.ledger.common.testkit.cpiPackageSummaryExample
import net.corda.ledger.common.testkit.cpkPackageSummaryListExample
import net.corda.ledger.common.testkit.getPrivacySalt
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.ledger.persistence.consensual.tests.datamodel.field
import net.corda.ledger.persistence.json.ContractStateVaultJsonFactoryRegistry
import net.corda.ledger.persistence.json.impl.DefaultContractStateVaultJsonFactoryImpl
import net.corda.ledger.persistence.utxo.CustomRepresentation
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.utxo.UtxoRepository
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.ledger.persistence.utxo.impl.UtxoPersistenceServiceImpl
import net.corda.ledger.persistence.utxo.tests.datamodel.UtxoEntityFactory
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.transaction.SignedLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup.METADATA
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup.NOTARY
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.data.transaction.UtxoTransactionMetadata
import net.corda.ledger.utxo.data.transaction.utxoComponentGroupStructure
import net.corda.libs.packaging.hash
import net.corda.orm.utils.transaction
import net.corda.persistence.common.getEntityManagerFactory
import net.corda.persistence.common.getSerializationService
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.test.util.dsl.entities.cpx.getCpkFileHashes
import net.corda.test.util.time.AutoTickTestClock
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.EncumbranceGroup
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.v5.ledger.utxo.observer.UtxoTokenFilterFields
import net.corda.v5.ledger.utxo.observer.UtxoTokenPoolKey
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Random
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.persistence.EntityManagerFactory

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
@Suppress("FunctionName", "MaxLineLength")
class UtxoPersistenceServiceImplTest {
    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var persistenceService: UtxoPersistenceService
    private lateinit var jsonMarshallingService: JsonMarshallingService
    private lateinit var jsonValidator: JsonValidator
    private lateinit var wireTransactionFactory: WireTransactionFactory
    private lateinit var digestService: DigestService
    private lateinit var serializationService: SerializationService
    private lateinit var entityManagerFactory: EntityManagerFactory
    private lateinit var repository: UtxoRepository
    private lateinit var cpiInfoReadService: CpiInfoReadService
    private lateinit var factoryRegistry: ContractStateVaultJsonFactoryRegistry
    private lateinit var filteredTransactionFactory: FilteredTransactionFactory
    private val emConfig = DbUtils.getEntityManagerConfiguration("ledger_db_for_test")

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var currentSandboxGroupContext: CurrentSandboxGroupContext

    companion object {
        // Truncating to millis as on Windows builds the micros are lost after fetching the data from Postgres
        private const val TESTING_DATAMODEL_CPB = "/META-INF/testing-datamodel.cpb"
        private const val TIMEOUT_MILLIS = 10000L
        private val testClock = AutoTickTestClock(
            Instant.now().truncatedTo(ChronoUnit.MILLIS),
            Duration.ofSeconds(1)
        )
        private val seedSequence = AtomicInteger((0..Int.MAX_VALUE / 2).random())
        private val notaryX500Name = MemberX500Name.parse("O=ExampleNotaryService, L=London, C=GB")
        private val publicKeyExample: PublicKey = KeyPairGenerator.getInstance("RSA")
            .also {
                it.initialize(512)
            }.genKeyPair().public
        private val notaryExampleName = notaryX500Name
        private val notaryExampleKey = publicKeyExample
        private val defaultInputStateRefs = listOf(StateRef(SecureHashImpl("SHA-256", ByteArray(12)), 1))
        private val defaultReferenceStateRefs = listOf(StateRef(SecureHashImpl("SHA-256", ByteArray(34)), 2))
        private val defaultVisibleTransactionOutputs = listOf(TestContractState1(), TestContractState2())
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
            val virtualNode = setup.fetchService<VirtualNodeService>(TIMEOUT_MILLIS)
            val virtualNodeInfo = virtualNode.load(TESTING_DATAMODEL_CPB)
            cpiInfoReadService = setup.fetchService(timeout = TIMEOUT_MILLIS)
            val cpkFileHashes = cpiInfoReadService.getCpkFileHashes(virtualNodeInfo)
            val ctx = virtualNode.entitySandboxService.get(virtualNodeInfo.holdingIdentity, cpkFileHashes)

            currentSandboxGroupContext.set(ctx)

            wireTransactionFactory = ctx.getSandboxSingletonService()
            jsonMarshallingService = ctx.getSandboxSingletonService()
            jsonValidator = ctx.getSandboxSingletonService()
            digestService = ctx.getSandboxSingletonService()
            serializationService = ctx.getSerializationService()
            entityManagerFactory = ctx.getEntityManagerFactory()
            repository = ctx.getSandboxSingletonService()
            factoryRegistry = ctx.getSandboxSingletonService()
            filteredTransactionFactory = ctx.getSandboxSingletonService()

            persistenceService = UtxoPersistenceServiceImpl(
                entityManagerFactory,
                repository,
                serializationService,
                digestService,
                factoryRegistry,
                DefaultContractStateVaultJsonFactoryImpl(),
                jsonMarshallingService,
                ctx.getSandboxSingletonService(),
                ctx.getSandboxSingletonService(),
                ctx.getSandboxSingletonService(),
                filteredTransactionFactory,
                ctx.getSandboxSingletonService(),
                testClock
            )
        }
    }

    @Suppress("Unused")
    @AfterAll
    fun cleanup() {
        emConfig.close()
        currentSandboxGroupContext.remove()
    }

    @Test
    fun `find signed transaction that matches input status`() {
        val entityFactory = UtxoEntityFactory(entityManagerFactory)
        val transaction = persistTransactionViaEntity(entityFactory)

        val retval = persistenceService.findSignedTransaction(transaction.id.toString(), UNVERIFIED)

        assertThat(retval).isEqualTo(transaction to "U")
    }

    @Test
    fun `find signed transaction with different status returns null to Status`() {
        val entityFactory = UtxoEntityFactory(entityManagerFactory)
        val transaction = persistTransactionViaEntity(entityFactory)

        val retval = persistenceService.findSignedTransaction(transaction.id.toString(), VERIFIED)

        assertThat(retval).isEqualTo(null to "U")
    }

    @Test
    fun `find ledger transaction that matches input status`() {
        val entityFactory = UtxoEntityFactory(entityManagerFactory)

        val transactions = listOf(
            persistTransactionViaEntity(entityFactory, VERIFIED),
            persistTransactionViaEntity(entityFactory, VERIFIED)
        )

        val inputStateRefs = listOf(StateRef(transactions[0].id, 0))
        val referenceStateRefs = listOf(StateRef(transactions[1].id, 1))

        val transaction = persistTransactionViaEntity(
            entityFactory,
            inputStateRefs = inputStateRefs,
            referenceStateRefs = referenceStateRefs
        )

        val resolvedInputStateRefs = persistenceService.resolveStateRefs(inputStateRefs)
        val resolvedReferenceStateRefs = persistenceService.resolveStateRefs(referenceStateRefs)

        val retval = persistenceService.findSignedLedgerTransaction(transaction.id.toString(), UNVERIFIED)

        assertThat(retval).isEqualTo(
            SignedLedgerTransactionContainer(
                transaction.wireTransaction,
                resolvedInputStateRefs,
                resolvedReferenceStateRefs,
                transaction.signatures
            ) to "U"
        )
    }

    @Test
    fun `find ledger transaction with different status returns null to Status`() {
        val entityFactory = UtxoEntityFactory(entityManagerFactory)
        val transaction = persistTransactionViaEntity(entityFactory)

        val retval = persistenceService.findSignedLedgerTransaction(transaction.id.toString(), VERIFIED)

        assertThat(retval).isEqualTo(null to "U")
    }

    @Test
    fun `find filtered transaction and signatures that matches with stateRefs`() {
        val entityFactory = UtxoEntityFactory(entityManagerFactory)
        val transactions = listOf(
            persistTransactionViaEntity(entityFactory, VERIFIED),
            persistTransactionViaEntity(entityFactory, VERIFIED)
        )

        val stateRefs = listOf(
            StateRef(transactions[0].id, 0),
            StateRef(transactions[1].id, 1),
        )
        val txIdToIndexes = stateRefs.groupBy { it.transactionId }
            .mapValues { (_, stateRefs) -> stateRefs.map { stateRef -> stateRef.index } }
        val expectedRetval = stateRefs.associate {
            val indexes = txIdToIndexes[it.transactionId]!!
            val signedTransaction = persistenceService.findSignedTransaction(it.transactionId.toString(), VERIFIED).first!!
            val filteredTransaction = createFilteredTransaction(signedTransaction, indexes)
            it.transactionId to Pair(filteredTransaction, signedTransaction.signatures)
        }

        val retval = persistenceService.findFilteredTransactionsAndSignatures(stateRefs)

        assertThat(retval).isEqualTo(expectedRetval)
    }

    @Test
    fun `find unconsumed visible transaction states`() {
        val entityFactory = UtxoEntityFactory(entityManagerFactory)
        val transaction1 = createSignedTransaction()
        val transaction2 = createSignedTransaction()
        entityManagerFactory.transaction { em ->

            em.createNativeQuery("DELETE FROM {h-schema}utxo_visible_transaction_output").executeUpdate()

            createTransactionEntity(entityFactory, transaction1, status = VERIFIED).also { em.persist(it) }
            createTransactionEntity(entityFactory, transaction2, status = VERIFIED).also { em.persist(it) }

            em.flush()

            val outputs = listOf(
                UtxoRepository.VisibleTransactionOutput(
                    1,
                    ContractState::class.java.name,
                    CustomRepresentation("{}"),
                    null,
                    notaryX500Name.toString()
                )
            )

            val outputs2 = listOf(
                UtxoRepository.VisibleTransactionOutput(
                    0,
                    ContractState::class.java.name,
                    CustomRepresentation("{}"),
                    null,
                    notaryX500Name.toString()
                ),
                UtxoRepository.VisibleTransactionOutput(
                    1,
                    ContractState::class.java.name,
                    CustomRepresentation("{}"),
                    null,
                    notaryX500Name.toString()
                )
            )

            repository.persistVisibleTransactionOutputs(em, transaction1.id.toString(), Instant.now(), outputs)
            repository.persistVisibleTransactionOutputs(em, transaction2.id.toString(), Instant.now(), outputs2)
            repository.markTransactionVisibleStatesConsumed(em, listOf(StateRef(transaction2.id, 1)), Instant.now())
        }

        val stateClass = TestContractState2::class.java
        val unconsumedStates = persistenceService.findUnconsumedVisibleStatesByType(stateClass)
        assertThat(unconsumedStates).isNotNull
        assertThat(unconsumedStates.size).isEqualTo(1)
        val visibleTransactionOutput = unconsumedStates.first()
        assertThat(visibleTransactionOutput.transactionId).isEqualTo(transaction1.id.toString())
        assertThat(visibleTransactionOutput.leafIndex).isEqualTo(1)
        assertThat(visibleTransactionOutput.info)
            .isEqualTo(transaction1.wireTransaction.componentGroupLists[UtxoComponentGroup.OUTPUTS_INFO.ordinal][1])
        assertThat(visibleTransactionOutput.data)
            .isEqualTo(transaction1.wireTransaction.componentGroupLists[UtxoComponentGroup.OUTPUTS.ordinal][1])
    }

    @Test
    fun `resolve staterefs`() {
        val entityFactory = UtxoEntityFactory(entityManagerFactory)
        val transactions = listOf(
            persistTransactionViaEntity(entityFactory, VERIFIED),
            persistTransactionViaEntity(entityFactory, VERIFIED)
        )

        val stateRefs = listOf(
            StateRef(transactions[0].id, 0),
            StateRef(transactions[1].id, 1),
        )
        val stateAndRefs = persistenceService.resolveStateRefs(stateRefs)
        assertThat(stateAndRefs).isNotNull
        assertThat(stateAndRefs.size).isEqualTo(2)

        for (i in 0..1) {
            val visibleTransactionOutput = stateAndRefs[i]

            assertThat(visibleTransactionOutput.transactionId).isEqualTo(transactions[i].id.toString())
            assertThat(visibleTransactionOutput.leafIndex).isEqualTo(i)
            assertThat(visibleTransactionOutput.info)
                .isEqualTo(
                    transactions[i].wireTransaction.componentGroupLists[UtxoComponentGroup.OUTPUTS_INFO.ordinal][i]
                )
            assertThat(visibleTransactionOutput.data)
                .isEqualTo(transactions[i].wireTransaction.componentGroupLists[UtxoComponentGroup.OUTPUTS.ordinal][i])
        }
    }

    @Test
    fun `update transaction status`() {
        var floorDateTime = nextTime()

        val entityFactory = UtxoEntityFactory(entityManagerFactory)
        val transaction = persistTransactionViaEntity(entityFactory)

        assertTransactionStatus(transaction.id.toString(), UNVERIFIED, entityFactory, floorDateTime)

        floorDateTime = nextTime()

        persistenceService.updateStatus(transaction.id.toString(), VERIFIED)

        assertTransactionStatus(transaction.id.toString(), VERIFIED, entityFactory, floorDateTime)
    }

    @Test
    fun `update transaction status does not affect other transactions`() {
        var floorDateTime = nextTime()

        val entityFactory = UtxoEntityFactory(entityManagerFactory)
        val transaction1 = persistTransactionViaEntity(entityFactory)
        val transaction2 = persistTransactionViaEntity(entityFactory)

        assertTransactionStatus(transaction1.id.toString(), UNVERIFIED, entityFactory, floorDateTime)
        assertTransactionStatus(transaction2.id.toString(), UNVERIFIED, entityFactory, floorDateTime)

        floorDateTime = nextTime()

        persistenceService.updateStatus(transaction1.id.toString(), VERIFIED)

        assertTransactionStatus(transaction1.id.toString(), VERIFIED, entityFactory, floorDateTime)
        assertTransactionStatus(transaction2.id.toString(), UNVERIFIED, entityFactory, floorDateTime)
    }

    @Test
    fun `persist signed transaction`() {
        val account = "Account"
        val transactionStatus = VERIFIED
        val signedTransaction = createSignedTransaction(signatures = createSignatures(Instant.now()))
        val visibleStatesIndexes = listOf(0)

        // Persist transaction
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            account,
            transactionStatus,
            visibleStatesIndexes
        )

        // Create the utxo tokens
        val tokenType = "token type"
        val tokenSymbol = "token symbol"
        val tokenTag = "token tag"
        val issuerHash = newRandomSecureHash()
        val ownerHash = newRandomSecureHash()
        val tokenAmount = BigDecimal(1)
        val utxoTokenMap = createUtxoTokenMap(
            transactionReader,
            tokenType,
            tokenSymbol,
            tokenTag,
            issuerHash,
            ownerHash,
            tokenAmount
        )

        persistenceService.persistTransaction(transactionReader, utxoTokenMap)

        val entityFactory = UtxoEntityFactory(entityManagerFactory)

        // Verify persisted data
        entityManagerFactory.transaction { em ->
            val dbTransaction = em.find(entityFactory.utxoTransaction, signedTransaction.id.toString())
            assertThat(dbTransaction).isNotNull
            val txPrivacySalt = dbTransaction.field<ByteArray>("privacySalt")
            val txAccountId = dbTransaction.field<String>("accountId")
            val txCreatedTs = dbTransaction.field<Instant>("created")

            assertThat(txPrivacySalt).isEqualTo(signedTransaction.wireTransaction.privacySalt.bytes)
            assertThat(txAccountId).isEqualTo(account)
            assertThat(txCreatedTs).isNotNull

            val componentGroupListsWithoutMetadata = signedTransaction.wireTransaction.componentGroupLists.drop(1)
            val txComponents = dbTransaction.field<Collection<Any>?>("components")
            assertThat(txComponents).isNotNull
                .hasSameSizeAs(componentGroupListsWithoutMetadata.flatten().filter { it.isNotEmpty() })
            txComponents!!
                .sortedWith(compareBy<Any> { it.field<Int>("groupIndex") }.thenBy { it.field<Int>("leafIndex") })
                .groupBy { it.field<Int>("groupIndex") }.values
                .zip(componentGroupListsWithoutMetadata)
                .forEachIndexed { groupIndex, (dbComponentGroup, componentGroup) ->
                    assertThat(dbComponentGroup).hasSameSizeAs(componentGroup)
                    dbComponentGroup.zip(componentGroup)
                        .forEachIndexed { leafIndex, (dbComponent, component) ->
                            assertThat(dbComponent.field<Int>("groupIndex")).isEqualTo(groupIndex + 1)
                            assertThat(dbComponent.field<Int>("leafIndex")).isEqualTo(leafIndex)
                            assertThat(dbComponent.field<ByteArray>("data")).isEqualTo(component)
                            assertThat(dbComponent.field<String>("hash")).isEqualTo(
                                digest("SHA-256", component).toString()
                            )
                        }
                }

            val dbMetadata = dbTransaction.field<Any>("metadata")
            assertThat(dbMetadata).isNotNull
            assertThat(dbMetadata.field<ByteArray>("canonicalData"))
                .isEqualTo(signedTransaction.wireTransaction.componentGroupLists[0][0])
            assertThat(dbMetadata.field<String>("groupParametersHash")).isNotNull
            assertThat(dbMetadata.field<String>("cpiFileChecksum")).isNotNull

            val dbTransactionSources = em.createNamedQuery(
                "UtxoTransactionSourceEntity.findByTransactionId",
                entityFactory.utxoTransactionSource
            )
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList

            assertThat(dbTransactionSources).allMatch {
                it.field<Int>("groupIndex") == UtxoComponentGroup.INPUTS.ordinal ||
                    it.field<Int>("groupIndex") == UtxoComponentGroup.REFERENCES.ordinal
            }

            val (dbTransactionInputs, dbTransactionReferences) = dbTransactionSources.partition {
                it.field<Int>("groupIndex") == UtxoComponentGroup.INPUTS.ordinal
            }

            assertThat(dbTransactionInputs).isNotNull
                .hasSameSizeAs(defaultInputStateRefs)

            assertThat(dbTransactionReferences).isNotNull
                .hasSameSizeAs(defaultReferenceStateRefs)

            dbTransactionInputs
                .sortedWith(compareBy<Any> { it.field<Int>("groupIndex") }.thenBy { it.field<Int>("leafIndex") })
                .zip(defaultInputStateRefs)
                .forEachIndexed { leafIndex, (dbInput, transactionInput) ->
                    assertThat(dbInput.field<Int>("leafIndex")).isEqualTo(leafIndex)
                    assertThat(dbInput.field<String>("refTransactionId")).isEqualTo(transactionInput.transactionId.toString())
                    assertThat(dbInput.field<Int>("refLeafIndex")).isEqualTo(transactionInput.index)
                }

            dbTransactionReferences
                .sortedWith(compareBy<Any> { it.field<Int>("groupIndex") }.thenBy { it.field<Int>("leafIndex") })
                .zip(defaultReferenceStateRefs)
                .forEachIndexed { leafIndex, (dbInput, transactionInput) ->
                    assertThat(dbInput.field<Int>("leafIndex")).isEqualTo(leafIndex)
                    assertThat(dbInput.field<String>("refTransactionId")).isEqualTo(transactionInput.transactionId.toString())
                    assertThat(dbInput.field<Int>("refLeafIndex")).isEqualTo(transactionInput.index)
                }

            val dbTransactionOutputs = em.createNamedQuery(
                "UtxoVisibleTransactionOutputEntity.findByTransactionId",
                entityFactory.utxoVisibleTransactionOutput
            )
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            assertThat(dbTransactionOutputs).isNotNull
                .hasSameSizeAs(componentGroupListsWithoutMetadata[UtxoComponentGroup.OUTPUTS.ordinal - 1])
            dbTransactionOutputs
                .sortedWith(compareBy<Any> { it.field<Int>("groupIndex") }.thenBy { it.field<Int>("leafIndex") })
                .zip(defaultVisibleTransactionOutputs)
                .forEachIndexed { leafIndex, (dbInput, transactionOutput) ->
                    assertThat(dbInput.field<Int>("groupIndex")).isEqualTo(UtxoComponentGroup.OUTPUTS.ordinal)
                    assertThat(dbInput.field<Int>("leafIndex")).isEqualTo(leafIndex)
                    assertThat(dbInput.field<String>("type")).isEqualTo(transactionOutput::class.java.canonicalName)
                    assertThat(dbInput.field<String>("tokenType")).isEqualTo(tokenType)
                    assertThat(dbInput.field<String>("tokenIssuerHash")).isEqualTo(issuerHash.toString())
                    assertThat(dbInput.field<String>("tokenNotaryX500Name")).isEqualTo(notaryX500Name.toString())
                    assertThat(dbInput.field<String>("tokenSymbol")).isEqualTo(tokenSymbol)
                    assertThat(dbInput.field<String>("tokenTag")).isEqualTo(tokenTag)
                    assertThat(dbInput.field<String>("tokenOwnerHash")).isEqualTo(ownerHash.toString())
                    assertThat(dbInput.field<BigDecimal>("tokenAmount")).isEqualTo(tokenAmount)
                    assertThat(dbInput.field<String>("customRepresentation").replace("\\s".toRegex(), ""))
                        .isEqualTo("{\"net.corda.v5.ledger.utxo.ContractState\":{\"stateRef\":\"${signedTransaction.id}:$leafIndex\"}}")
                    assertThat(dbInput.field<Instant>("consumed")).isNull()
                }

            val signatures = signedTransaction.signatures
            val txSignatures = dbTransaction.field<Collection<Any>?>("signatures")
            assertThat(txSignatures)
                .isNotNull
                .hasSameSizeAs(signatures)
            txSignatures!!
                .sortedBy { it.field<Int>("index") }
                .zip(signatures)
                .forEachIndexed { index, (dbSignature, signature) ->
                    assertThat(dbSignature.field<Int>("index")).isEqualTo(index)
                    assertThat(dbSignature.field<ByteArray>("signature")).isEqualTo(
                        serializationService.serialize(
                            signature
                        ).bytes
                    )
                    assertThat(dbSignature.field<String>("publicKeyHash")).isEqualTo(
                        signature.by.toString()
                    )
                    assertThat(dbSignature.field<Instant>("created")).isEqualTo(txCreatedTs)
                }

            assertThat(dbTransaction.field<String>("status")).isEqualTo(transactionStatus.value)
            assertThat(dbTransaction.field<Instant>("updated")).isEqualTo(txCreatedTs)
        }
    }

    @Test
    fun `persist and find signed group parameter`() {
        val signedGroupParameters = SignedGroupParameters(
            ByteBuffer.wrap(ByteArray(1)),
            CryptoSignatureWithKey(
                ByteBuffer.wrap(ByteArray(1)),
                ByteBuffer.wrap(ByteArray(1))
            ),
            CryptoSignatureSpec("", null, null)
        )

        val hash = signedGroupParameters.groupParameters.array().hash(DigestAlgorithmName.SHA2_256).toString()

        persistenceService.persistSignedGroupParametersIfDoNotExist(signedGroupParameters)

        val persistedSignedGroupParameters = persistenceService.findSignedGroupParameters(hash)

        assertThat(
            persistedSignedGroupParameters?.mgmSignature?.publicKey.toString()
        )
            .isEqualTo(signedGroupParameters.mgmSignature?.publicKey.toString())
        assertThat(
            persistedSignedGroupParameters?.mgmSignatureSpec.toString()
        )
            .isEqualTo(signedGroupParameters.mgmSignatureSpec.toString())
    }

    @Test
    fun `persist and find filtered transactions`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val account = "Account"

        val filteredTransactionToStore = createFilteredTransaction(signedTransaction)

        persistenceService.persistFilteredTransactions(
            mapOf(filteredTransactionToStore to signatures),
            account
        )

        val filteredTxResults = (persistenceService as UtxoPersistenceServiceImpl).findFilteredTransactions(
            listOf(filteredTransactionToStore.id.toString())
        )

        assertThat(filteredTxResults).hasSize(1)

        val storedFilteredTransaction = filteredTxResults[filteredTransactionToStore.id]?.first

        assertNotNull(storedFilteredTransaction)

        assertThat(storedFilteredTransaction!!.id).isEqualTo(filteredTransactionToStore.id)
        assertThat(storedFilteredTransaction.metadata).isEqualTo(filteredTransactionToStore.metadata)
        assertThat(storedFilteredTransaction.privacySalt).isEqualTo(filteredTransactionToStore.privacySalt)
        assertThat(storedFilteredTransaction.filteredComponentGroups).isEqualTo(filteredTransactionToStore.filteredComponentGroups)
        assertThat(storedFilteredTransaction.topLevelMerkleProof).isEqualTo(filteredTransactionToStore.topLevelMerkleProof)

        // Check that outputs / outputs_info merkle proofs are matching
        assertThat(storedFilteredTransaction.filteredComponentGroups[UtxoComponentGroup.OUTPUTS.ordinal]?.merkleProof).isEqualTo(
            filteredTransactionToStore.filteredComponentGroups[UtxoComponentGroup.OUTPUTS.ordinal]?.merkleProof
        )
        assertThat(storedFilteredTransaction.filteredComponentGroups[UtxoComponentGroup.OUTPUTS_INFO.ordinal]?.merkleProof).isEqualTo(
            filteredTransactionToStore.filteredComponentGroups[UtxoComponentGroup.OUTPUTS_INFO.ordinal]?.merkleProof
        )

        storedFilteredTransaction.verify()
    }

    @Test
    fun `filtered transaction cannot be persisted if no metadata is present`() {
        val signedTransaction = createSignedTransaction()
        val account = "Account"

        val filteredTransaction = createFilteredTransaction(signedTransaction)
        val noMetadataFtx = filteredTransactionFactory.create(
            filteredTransaction.id,
            filteredTransaction.topLevelMerkleProof,
            filteredTransaction.filteredComponentGroups.filter { it.key != 0 },
            filteredTransaction.privacySalt.bytes
        )

        assertThatThrownBy { persistenceService.persistFilteredTransactions(mapOf(noMetadataFtx to emptyList()), account) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasStackTraceContaining("Could not find metadata in the filtered transaction with id: ${filteredTransaction.id}")
    }

    @Test
    fun `persist filtered transaction when a verified signed transaction exists for the same id does not overwrite the existing transaction record`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val entityFactory = UtxoEntityFactory(entityManagerFactory)

        entityManagerFactory.transaction { em ->
            createTransactionEntity(entityFactory, signedTransaction, status = VERIFIED).also { em.persist(it) }
        }

        val (createdTimestamp, updatedTimestamp) = entityManagerFactory.transaction { em ->
            val transaction = em.find(entityFactory.utxoTransaction, signedTransaction.id.toString())
            assertThat(transaction).isNotNull
            assertThat(transaction.field<Boolean>("isFiltered")).isFalse()
            assertThat(transaction.field<String>("status")).isEqualTo("V")
            transaction.field<Instant>("created") to transaction.field<Instant>("updated")
        }

        val filteredTransaction = createFilteredTransaction(signedTransaction)

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")

        entityManagerFactory.transaction { em ->
            val transaction = em.find(entityFactory.utxoTransaction, signedTransaction.id.toString())
            assertThat(transaction).isNotNull
            assertThat(transaction.field<Boolean>("isFiltered")).isFalse()
            assertThat(transaction.field<String>("status")).isEqualTo("V")
            assertThat(transaction.field<Instant>("created")).isEqualTo(createdTimestamp)
            assertThat(transaction.field<Instant>("updated")).isEqualTo(updatedTimestamp)
        }
    }

    @Test
    fun `persist filtered transaction when a verified signed transaction exists for the same id still persists to the proof tables`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val entityFactory = UtxoEntityFactory(entityManagerFactory)

        entityManagerFactory.transaction { em ->
            createTransactionEntity(entityFactory, signedTransaction, status = VERIFIED).also { em.persist(it) }
        }

        entityManagerFactory.transaction { em ->
            val proofs = em.createNamedQuery("UtxoMerkleProofEntity.findByTransactionId", entityFactory.merkleProof)
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            assertThat(proofs).isEmpty()
        }

        val filteredTransaction = createFilteredTransaction(signedTransaction)

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")

        entityManagerFactory.transaction { em ->
            val proofs = em.createNamedQuery("UtxoMerkleProofEntity.findByTransactionId", entityFactory.merkleProof)
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            assertThat(proofs).hasSize(5)
        }
    }

    @Test
    fun `persist filtered transaction when an unverified signed transaction exists for the same id sets is_filtered to true and leaves the status as UNVERIFIED`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val entityFactory = UtxoEntityFactory(entityManagerFactory)

        entityManagerFactory.transaction { em ->
            createTransactionEntity(entityFactory, signedTransaction, status = UNVERIFIED).also { em.persist(it) }
        }

        val (createdTimestamp, updatedTimestamp) = entityManagerFactory.transaction { em ->
            val transaction = em.find(entityFactory.utxoTransaction, signedTransaction.id.toString())
            assertThat(transaction).isNotNull
            assertThat(transaction.field<Boolean>("isFiltered")).isFalse()
            assertThat(transaction.field<String>("status")).isEqualTo("U")
            transaction.field<Instant>("created") to transaction.field<Instant>("updated")
        }

        val filteredTransaction = createFilteredTransaction(signedTransaction)

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")

        entityManagerFactory.transaction { em ->
            val transaction = em.find(entityFactory.utxoTransaction, signedTransaction.id.toString())
            assertThat(transaction).isNotNull
            assertThat(transaction.field<Boolean>("isFiltered")).isTrue()
            assertThat(transaction.field<String>("status")).isEqualTo("U")
            assertThat(transaction.field<Instant>("created")).isEqualTo(createdTimestamp)
            assertThat(transaction.field<Instant>("updated")).isEqualTo(updatedTimestamp)
        }
    }

    @Test
    fun `persist filtered transaction when an unverified signed transaction exists for the same id still persists to the proof tables`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val entityFactory = UtxoEntityFactory(entityManagerFactory)

        entityManagerFactory.transaction { em ->
            createTransactionEntity(entityFactory, signedTransaction, status = UNVERIFIED).also { em.persist(it) }
        }

        entityManagerFactory.transaction { em ->
            val proofs = em.createNamedQuery("UtxoMerkleProofEntity.findByTransactionId", entityFactory.merkleProof)
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            assertThat(proofs).isEmpty()
        }

        val filteredTransaction = createFilteredTransaction(signedTransaction)

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")

        entityManagerFactory.transaction { em ->
            val proofs = em.createNamedQuery("UtxoMerkleProofEntity.findByTransactionId", entityFactory.merkleProof)
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            assertThat(proofs).hasSize(5)
        }
    }

    @Test
    fun `persist filtered transaction when a draft signed transaction exists for the same id sets is_filtered to true and leaves the status as DRAFT`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val entityFactory = UtxoEntityFactory(entityManagerFactory)

        entityManagerFactory.transaction { em ->
            createTransactionEntity(entityFactory, signedTransaction, status = DRAFT).also { em.persist(it) }
        }

        val (createdTimestamp, updatedTimestamp) = entityManagerFactory.transaction { em ->
            val transaction = em.find(entityFactory.utxoTransaction, signedTransaction.id.toString())
            assertThat(transaction).isNotNull
            assertThat(transaction.field<Boolean>("isFiltered")).isFalse()
            assertThat(transaction.field<String>("status")).isEqualTo("D")
            transaction.field<Instant>("created") to transaction.field<Instant>("updated")
        }

        val filteredTransaction = createFilteredTransaction(signedTransaction)

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")

        entityManagerFactory.transaction { em ->
            val transaction = em.find(entityFactory.utxoTransaction, signedTransaction.id.toString())
            assertThat(transaction).isNotNull
            assertThat(transaction.field<Boolean>("isFiltered")).isTrue()
            assertThat(transaction.field<String>("status")).isEqualTo("D")
            assertThat(transaction.field<Instant>("created")).isEqualTo(createdTimestamp)
            assertThat(transaction.field<Instant>("updated")).isEqualTo(updatedTimestamp)
        }
    }

    @Test
    fun `persist filtered transaction when a draft signed transaction exists for the same id still persists to the proof tables`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val entityFactory = UtxoEntityFactory(entityManagerFactory)

        entityManagerFactory.transaction { em ->
            createTransactionEntity(entityFactory, signedTransaction, status = DRAFT).also { em.persist(it) }
        }

        entityManagerFactory.transaction { em ->
            val proofs = em.createNamedQuery("UtxoMerkleProofEntity.findByTransactionId", entityFactory.merkleProof)
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            assertThat(proofs).isEmpty()
        }

        val filteredTransaction = createFilteredTransaction(signedTransaction)

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")

        entityManagerFactory.transaction { em ->
            val proofs = em.createNamedQuery("UtxoMerkleProofEntity.findByTransactionId", entityFactory.merkleProof)
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            assertThat(proofs).hasSize(5)
        }
    }

    @Test
    fun `persist filtered transaction when a filtered transaction exists for the same id does not overwrite the existing transaction record`() {
        val outputStates = listOf(TestContractState1(), TestContractState2())
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(outputStates = outputStates, signatures = signatures)
        val filteredTransaction1 = createFilteredTransaction(signedTransaction, indexes = listOf(0))
        val filteredTransaction2 = createFilteredTransaction(signedTransaction, indexes = listOf(1))
        val entityFactory = UtxoEntityFactory(entityManagerFactory)

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction1 to signatures), "account")

        val (createdTimestamp, updatedTimestamp) = entityManagerFactory.transaction { em ->
            val transaction = em.find(entityFactory.utxoTransaction, signedTransaction.id.toString())
            assertThat(transaction).isNotNull
            assertThat(transaction.field<Boolean>("isFiltered")).isTrue()
            assertThat(transaction.field<String>("status")).isEqualTo("V")
            transaction.field<Instant>("created") to transaction.field<Instant>("updated")
        }

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction2 to signatures), "account")

        entityManagerFactory.transaction { em ->
            val transaction = em.find(entityFactory.utxoTransaction, signedTransaction.id.toString())
            assertThat(transaction).isNotNull
            assertThat(transaction.field<Boolean>("isFiltered")).isTrue()
            assertThat(transaction.field<String>("status")).isEqualTo("V")
            assertThat(transaction.field<Instant>("created")).isEqualTo(createdTimestamp)
            assertThat(transaction.field<Instant>("updated")).isEqualTo(updatedTimestamp)
        }
    }

    @Test
    fun `persist filtered transaction when a filtered transaction exists for the same id updates the proof tables`() {
        val outputStates = listOf(TestContractState1(), TestContractState1(), TestContractState2())
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(outputStates = outputStates, signatures = signatures)
        val filteredTransaction1 = createFilteredTransaction(signedTransaction, indexes = listOf(0))
        val filteredTransaction2 = createFilteredTransaction(signedTransaction, indexes = listOf(1))
        val filteredTransaction3 = createFilteredTransaction(signedTransaction, indexes = listOf(2))
        val entityFactory = UtxoEntityFactory(entityManagerFactory)

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction1 to signatures), "account")

        val merkleProofIds1 = entityManagerFactory.transaction { em ->
            val proofs = em.createNamedQuery("UtxoMerkleProofEntity.findByTransactionId", entityFactory.merkleProof)
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            proofs.map { it.field<String>("merkleProofId") }
        }

        assertThat(merkleProofIds1).hasSize(5)

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction2 to signatures), "account")

        val merkleProofIds2 = entityManagerFactory.transaction { em ->
            val proofs = em.createNamedQuery("UtxoMerkleProofEntity.findByTransactionId", entityFactory.merkleProof)
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            proofs.map { it.field<String>("merkleProofId") }
        }

        // Only the output and output info groups change, so only 2 new rows are inserted into the database
        assertThat(merkleProofIds2).hasSize(7)
        assertThat(merkleProofIds2).containsAll(merkleProofIds1)
        assertThat(merkleProofIds2 - merkleProofIds1).hasSize(2)

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction3 to signatures), "account")

        val merkleProofIds3 = entityManagerFactory.transaction { em ->
            val proofs = em.createNamedQuery("UtxoMerkleProofEntity.findByTransactionId", entityFactory.merkleProof)
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            proofs.map { it.field<String>("merkleProofId") }
        }

        // Only the output and output info groups change, so only 2 new rows are inserted into the database
        assertThat(merkleProofIds3).hasSize(9)
        assertThat(merkleProofIds3).containsAll(merkleProofIds1)
        assertThat(merkleProofIds3).containsAll(merkleProofIds2)
        assertThat(merkleProofIds3 - merkleProofIds1 - merkleProofIds2).hasSize(2)

        val loadedFilteredTransactions =
            (persistenceService as UtxoPersistenceServiceImpl).findFilteredTransactions(listOf(signedTransaction.id.toString()))

        assertThat(loadedFilteredTransactions).hasSize(1)
        assertThat(loadedFilteredTransactions[signedTransaction.id]).isNotNull

        val loadedFilteredTransaction = loadedFilteredTransactions[signedTransaction.id]?.first!!
        assertThat(loadedFilteredTransaction.filteredComponentGroups).hasSize(4)
        assertThat(loadedFilteredTransaction.filteredComponentGroups.keys).containsOnly(0, 1, 3, 8)
        assertThat(loadedFilteredTransaction.getComponentGroupContent(8)?.get(0)?.second).isEqualTo(outputStates[0].toBytes())
        assertThat(loadedFilteredTransaction.getComponentGroupContent(8)?.get(1)?.second).isEqualTo(outputStates[1].toBytes())
        assertThat(loadedFilteredTransaction.getComponentGroupContent(8)?.get(2)?.second).isEqualTo(outputStates[2].toBytes())

        loadedFilteredTransaction.verify()
    }

    @Test
    fun `persist filtered transaction when the exact same filtered transaction exists does not update proof tables`() {
        val outputStates = listOf(TestContractState1(), TestContractState2())
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(outputStates = outputStates, signatures = signatures)
        val filteredTransaction = createFilteredTransaction(signedTransaction, indexes = listOf(0, 1))
        val entityFactory = UtxoEntityFactory(entityManagerFactory)

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")

        val merkleProofIds1 = entityManagerFactory.transaction { em ->
            val proofs = em.createNamedQuery("UtxoMerkleProofEntity.findByTransactionId", entityFactory.merkleProof)
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            proofs.map { it.field<String>("merkleProofId") }
        }

        assertThat(merkleProofIds1).hasSize(5)

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")

        val merkleProofIds2 = entityManagerFactory.transaction { em ->
            val proofs = em.createNamedQuery("UtxoMerkleProofEntity.findByTransactionId", entityFactory.merkleProof)
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            proofs.map { it.field<String>("merkleProofId") }
        }

        assertThat(merkleProofIds2).hasSize(5)
        assertThat(merkleProofIds2).containsOnlyOnceElementsOf(merkleProofIds1)

        val loadedFilteredTransactions =
            (persistenceService as UtxoPersistenceServiceImpl).findFilteredTransactions(listOf(signedTransaction.id.toString()))

        assertThat(loadedFilteredTransactions).hasSize(1)
        assertThat(loadedFilteredTransactions[signedTransaction.id]).isNotNull

        val loadedFilteredTransaction = loadedFilteredTransactions[signedTransaction.id]?.first!!
        assertThat(loadedFilteredTransaction.filteredComponentGroups).hasSize(4)
        assertThat(loadedFilteredTransaction.filteredComponentGroups.keys).containsOnly(0, 1, 3, 8)
        assertThat(loadedFilteredTransaction.getComponentGroupContent(8)?.get(0)?.second).isEqualTo(outputStates[0].toBytes())
        assertThat(loadedFilteredTransaction.getComponentGroupContent(8)?.get(1)?.second).isEqualTo(outputStates[1].toBytes())

        loadedFilteredTransaction.verify()
    }

    @Test
    fun `persist verified signed transaction when a filtered transaction exists for the same id overwrites the existing transaction record`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val filteredTransaction = createFilteredTransaction(signedTransaction)
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            "account",
            VERIFIED,
            emptyList()
        )
        val entityFactory = UtxoEntityFactory(entityManagerFactory)

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")

        val (createdTimestamp, updatedTimestamp) = entityManagerFactory.transaction { em ->
            val transaction = em.find(entityFactory.utxoTransaction, signedTransaction.id.toString())
            assertThat(transaction).isNotNull
            assertThat(transaction.field<Boolean>("isFiltered")).isTrue()
            assertThat(transaction.field<String>("status")).isEqualTo("V")
            transaction.field<Instant>("created") to transaction.field<Instant>("updated")
        }

        persistenceService.persistTransaction(transactionReader, emptyMap())

        entityManagerFactory.transaction { em ->
            val transaction = em.find(entityFactory.utxoTransaction, signedTransaction.id.toString())
            assertThat(transaction).isNotNull
            assertThat(transaction.field<Boolean>("isFiltered")).isFalse()
            assertThat(transaction.field<String>("status")).isEqualTo("V")
            assertThat(transaction.field<Instant>("created")).isEqualTo(createdTimestamp)
            assertThat(transaction.field<Instant>("updated")).isAfter(updatedTimestamp)
        }
    }

    @Test
    fun `persist verified signed transaction when a filtered transaction exists for the same id leaves the existing proof table data`() {
        val outputStates = listOf(TestContractState1(), TestContractState2())
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(outputStates = outputStates, signatures = signatures)
        val filteredTransaction = createFilteredTransaction(signedTransaction, indexes = listOf(0, 1))
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            "account",
            VERIFIED,
            emptyList()
        )
        val entityFactory = UtxoEntityFactory(entityManagerFactory)

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")

        entityManagerFactory.transaction { em ->
            val proofs = em.createNamedQuery("UtxoMerkleProofEntity.findByTransactionId", entityFactory.merkleProof)
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            assertThat(proofs).hasSize(5)
        }

        persistenceService.persistTransaction(transactionReader, emptyMap())

        entityManagerFactory.transaction { em ->
            val proofs = em.createNamedQuery("UtxoMerkleProofEntity.findByTransactionId", entityFactory.merkleProof)
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            assertThat(proofs).hasSize(5)
        }
    }

    @Test
    fun `persist unverified signed transaction when a filtered transaction exists for the same id sets the status to UNVERIFIED and leaves is_filtered as true`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val filteredTransaction = createFilteredTransaction(signedTransaction)
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            "account",
            UNVERIFIED,
            emptyList()
        )
        val entityFactory = UtxoEntityFactory(entityManagerFactory)

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")

        val (createdTimestamp, updatedTimestamp) = entityManagerFactory.transaction { em ->
            val transaction = em.find(entityFactory.utxoTransaction, signedTransaction.id.toString())
            assertThat(transaction).isNotNull
            assertThat(transaction.field<Boolean>("isFiltered")).isTrue()
            assertThat(transaction.field<String>("status")).isEqualTo("V")
            transaction.field<Instant>("created") to transaction.field<Instant>("updated")
        }

        persistenceService.persistTransaction(transactionReader, emptyMap())

        entityManagerFactory.transaction { em ->
            val transaction = em.find(entityFactory.utxoTransaction, signedTransaction.id.toString())
            assertThat(transaction).isNotNull
            assertThat(transaction.field<Boolean>("isFiltered")).isTrue()
            assertThat(transaction.field<String>("status")).isEqualTo("U")
            assertThat(transaction.field<Instant>("created")).isEqualTo(createdTimestamp)
            assertThat(transaction.field<Instant>("updated")).isAfter(updatedTimestamp)
        }
    }

    @Test
    fun `persist unverified signed transaction when a filtered transaction exists for the same id leaves the existing proof table data`() {
        val outputStates = listOf(TestContractState1(), TestContractState2())
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(outputStates = outputStates, signatures = signatures)
        val filteredTransaction = createFilteredTransaction(signedTransaction, indexes = listOf(0, 1))
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            "account",
            UNVERIFIED,
            emptyList()
        )
        val entityFactory = UtxoEntityFactory(entityManagerFactory)

        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")

        entityManagerFactory.transaction { em ->
            val proofs = em.createNamedQuery("UtxoMerkleProofEntity.findByTransactionId", entityFactory.merkleProof)
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            assertThat(proofs).hasSize(5)
        }

        persistenceService.persistTransaction(transactionReader, emptyMap())

        entityManagerFactory.transaction { em ->
            val proofs = em.createNamedQuery("UtxoMerkleProofEntity.findByTransactionId", entityFactory.merkleProof)
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            assertThat(proofs).hasSize(5)
        }
    }

    @Test
    fun `find a signed transaction after persisting a verified signed transaction when a filtered transaction existed previously returns the signed transaction`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val filteredTransaction = createFilteredTransaction(signedTransaction)
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            "account",
            VERIFIED,
            emptyList()
        )
        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")
        persistenceService.persistTransaction(transactionReader, emptyMap())
        val (loadedSignedTransaction, _) = persistenceService.findSignedTransaction(signedTransaction.id.toString(), VERIFIED)
        assertThat(loadedSignedTransaction).isEqualTo(signedTransaction)
    }

    @Test
    fun `find a signed transaction after persisting a filtered transaction when a verified signed transaction existed previously returns the signed transaction`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val filteredTransaction = createFilteredTransaction(signedTransaction)
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            "account",
            VERIFIED,
            emptyList()
        )
        persistenceService.persistTransaction(transactionReader, emptyMap())
        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")
        val (loadedSignedTransaction, _) = persistenceService.findSignedTransaction(signedTransaction.id.toString(), VERIFIED)
        assertThat(loadedSignedTransaction).isEqualTo(signedTransaction)
    }

    @Test
    fun `find a signed transaction when only a filtered transaction exists returns nothing`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val filteredTransaction = createFilteredTransaction(signedTransaction)
        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")
        assertThat(persistenceService.findSignedTransaction(signedTransaction.id.toString(), VERIFIED).first).isNull()
        assertThat(persistenceService.findSignedTransaction(signedTransaction.id.toString(), UNVERIFIED).first).isNull()
        assertThat(persistenceService.findSignedTransaction(signedTransaction.id.toString(), DRAFT).first).isNull()
        assertThat(persistenceService.findSignedTransaction(signedTransaction.id.toString(), INVALID).first).isNull()
    }

    @Test
    fun `find a signed transaction when only an unverified transaction exists`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            "account",
            UNVERIFIED,
            emptyList()
        )
        persistenceService.persistTransaction(transactionReader, emptyMap())
        assertThat(persistenceService.findSignedTransaction(signedTransaction.id.toString(), VERIFIED).first).isNull()
        assertThat(persistenceService.findSignedTransaction(signedTransaction.id.toString(), UNVERIFIED).first).isEqualTo(signedTransaction)
        assertThat(persistenceService.findSignedTransaction(signedTransaction.id.toString(), DRAFT).first).isNull()
        assertThat(persistenceService.findSignedTransaction(signedTransaction.id.toString(), INVALID).first).isNull()
    }

    @Test
    fun `find a signed transaction when an unverified transaction and filtered transaction exist for the same id`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val filteredTransaction = createFilteredTransaction(signedTransaction)
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            "account",
            UNVERIFIED,
            emptyList()
        )
        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")
        persistenceService.persistTransaction(transactionReader, emptyMap())
        assertThat(persistenceService.findSignedTransaction(signedTransaction.id.toString(), VERIFIED).first).isNull()
        assertThat(persistenceService.findSignedTransaction(signedTransaction.id.toString(), UNVERIFIED).first).isEqualTo(signedTransaction)
        assertThat(persistenceService.findSignedTransaction(signedTransaction.id.toString(), DRAFT).first).isNull()
        assertThat(persistenceService.findSignedTransaction(signedTransaction.id.toString(), INVALID).first).isNull()
    }

    @Test
    fun `find a filtered transaction when only a verified signed transaction exists filters the verified transaction`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val filteredTransaction = createFilteredTransaction(signedTransaction)
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            "account",
            VERIFIED,
            emptyList()
        )
        persistenceService.persistTransaction(transactionReader, emptyMap())
        val loadedFilteredTransactions = (persistenceService as UtxoPersistenceServiceImpl)
            .findFilteredTransactions(listOf(signedTransaction.id.toString()))
        assertThat(loadedFilteredTransactions).isEmpty()

        val foundFilteredTransactions = (persistenceService as UtxoPersistenceServiceImpl)
            .findFilteredTransactionsAndSignatures(listOf(StateRef(signedTransaction.id, 0), StateRef(signedTransaction.id, 1)))
        assertThat(foundFilteredTransactions).containsOnlyKeys(signedTransaction.id)
        assertThat(foundFilteredTransactions[signedTransaction.id]?.first).isEqualTo(filteredTransaction)
        assertThat(foundFilteredTransactions[signedTransaction.id]?.second).isEqualTo(signatures)
    }

    @Test
    fun `find a filtered transaction when only an unverified transaction exists returns nothing`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            "account",
            UNVERIFIED,
            emptyList()
        )
        persistenceService.persistTransaction(transactionReader, emptyMap())
        val loadedFilteredTransactions = (persistenceService as UtxoPersistenceServiceImpl)
            .findFilteredTransactions(listOf(signedTransaction.id.toString()))
        assertThat(loadedFilteredTransactions).isEmpty()

        val foundFilteredTransactions = (persistenceService as UtxoPersistenceServiceImpl)
            .findFilteredTransactionsAndSignatures(listOf(StateRef(signedTransaction.id, 0), StateRef(signedTransaction.id, 1)))
        assertThat(foundFilteredTransactions[signedTransaction.id]).isEqualTo(null to emptyList<DigitalSignatureAndMetadata>())
    }

    @Test
    fun `find a filtered transaction when only a draft signed transaction exists returns nothing`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            "account",
            DRAFT,
            emptyList()
        )
        persistenceService.persistTransaction(transactionReader, emptyMap())
        val loadedFilteredTransactions = (persistenceService as UtxoPersistenceServiceImpl)
            .findFilteredTransactions(listOf(signedTransaction.id.toString()))
        assertThat(loadedFilteredTransactions).isEmpty()

        val foundFilteredTransactions = (persistenceService as UtxoPersistenceServiceImpl)
            .findFilteredTransactionsAndSignatures(listOf(StateRef(signedTransaction.id, 0), StateRef(signedTransaction.id, 1)))
        assertThat(foundFilteredTransactions[signedTransaction.id]).isEqualTo(null to emptyList<DigitalSignatureAndMetadata>())
    }

    @Test
    fun `find a filtered transaction when an unverified transaction and filtered transaction exist for the same id returns the filtered transaction`() {
        val signatures = createSignatures(Instant.now())
        val outputStates = defaultVisibleTransactionOutputs
        val signedTransaction = createSignedTransaction(outputStates = outputStates, signatures = signatures)
        val filteredTransaction = createFilteredTransaction(signedTransaction, indexes = listOf(1))
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            "account",
            UNVERIFIED,
            emptyList()
        )
        persistenceService.persistTransaction(transactionReader, emptyMap())
        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")
        val loadedFilteredTransactions = (persistenceService as UtxoPersistenceServiceImpl)
            .findFilteredTransactions(listOf(signedTransaction.id.toString()))
        assertThat(loadedFilteredTransactions).hasSize(1)
        assertThat(loadedFilteredTransactions[signedTransaction.id]?.first).isEqualTo(filteredTransaction)

        val foundFilteredTransactions = (persistenceService as UtxoPersistenceServiceImpl)
            .findFilteredTransactionsAndSignatures(listOf(StateRef(signedTransaction.id, 0), StateRef(signedTransaction.id, 1)))
        assertThat(foundFilteredTransactions).containsOnlyKeys(signedTransaction.id)
        val foundFilteredTransaction = foundFilteredTransactions[signedTransaction.id]?.first
        val filteredOutputStates = foundFilteredTransaction?.getComponentGroupContent(8)
        // Check that it only sees what is in the filtered transaction and not the unverified transaction
        assertThat(foundFilteredTransaction).isEqualTo(filteredTransaction)
        assertThat(filteredOutputStates).hasSize(1)
        assertThat(filteredOutputStates?.get(0)?.first).isEqualTo(1)
        assertThat(filteredOutputStates?.get(0)?.second).isEqualTo(outputStates[1].toBytes())
        assertThat(foundFilteredTransactions[signedTransaction.id]?.second).isEqualTo(signatures)
    }

    @Test
    fun `find a filtered transaction after persisting a verified signed transaction when a filtered transaction existed previously filters the signed transaction`() {
        val signatures = createSignatures(Instant.now())
        val signedTransaction = createSignedTransaction(signatures = signatures)
        val filteredTransaction = createFilteredTransaction(signedTransaction)
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            "account",
            VERIFIED,
            emptyList()
        )
        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")
        persistenceService.persistTransaction(transactionReader, emptyMap())
        // Returns the filtered transaction here, which bypasses the signed transaction filtering
        val loadedFilteredTransactions = (persistenceService as UtxoPersistenceServiceImpl)
            .findFilteredTransactions(listOf(signedTransaction.id.toString()))
        assertThat(loadedFilteredTransactions).hasSize(1)
        assertThat(loadedFilteredTransactions[signedTransaction.id]?.first).isEqualTo(filteredTransaction)
        // Calling [findFilteredTransactionsAndSignatures] does hit the signed transaction filtering
        val foundFilteredTransactions = (persistenceService as UtxoPersistenceServiceImpl)
            .findFilteredTransactionsAndSignatures(listOf(StateRef(signedTransaction.id, 0), StateRef(signedTransaction.id, 1)))
        assertThat(foundFilteredTransactions).containsOnlyKeys(signedTransaction.id)
        assertThat(foundFilteredTransactions[signedTransaction.id]?.first).isEqualTo(filteredTransaction)
        assertThat(foundFilteredTransactions[signedTransaction.id]?.second).isEqualTo(signatures)
    }

    @Test
    fun `find a filtered transaction after persisting a verified signed transaction when a filtered transaction with filtered leaves existed previously filters the signed transaction`() {
        val signatures = createSignatures(Instant.now())
        val outputStates = defaultVisibleTransactionOutputs
        val signedTransaction = createSignedTransaction(outputStates = outputStates, signatures = signatures)
        val filteredTransaction = createFilteredTransaction(signedTransaction, indexes = listOf(1))
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            "account",
            VERIFIED,
            emptyList()
        )
        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")
        persistenceService.persistTransaction(transactionReader, emptyMap())
        // Returns the filtered transaction here, which bypasses the signed transaction filtering
        val loadedFilteredTransactions = (persistenceService as UtxoPersistenceServiceImpl)
            .findFilteredTransactions(listOf(signedTransaction.id.toString()))
        assertThat(loadedFilteredTransactions).hasSize(1)
        assertThat(loadedFilteredTransactions[signedTransaction.id]?.first).isEqualTo(filteredTransaction)
        // Calling [findFilteredTransactionsAndSignatures] does hit the signed transaction filtering
        val foundFilteredTransactions = (persistenceService as UtxoPersistenceServiceImpl)
            .findFilteredTransactionsAndSignatures(listOf(StateRef(signedTransaction.id, 0), StateRef(signedTransaction.id, 1)))
        assertThat(foundFilteredTransactions).containsOnlyKeys(signedTransaction.id)
        val foundFilteredTransaction = foundFilteredTransactions[signedTransaction.id]?.first
        val filteredOutputStates = foundFilteredTransaction?.getComponentGroupContent(8)
        assertThat(foundFilteredTransaction).isNotEqualTo(filteredTransaction)
        assertThat(filteredOutputStates).hasSize(2)
        assertThat(filteredOutputStates?.get(0)?.first).isEqualTo(0)
        assertThat(filteredOutputStates?.get(0)?.second).isEqualTo(outputStates[0].toBytes())
        assertThat(filteredOutputStates?.get(1)?.first).isEqualTo(1)
        assertThat(filteredOutputStates?.get(1)?.second).isEqualTo(outputStates[1].toBytes())
        assertThat(foundFilteredTransactions[signedTransaction.id]?.second).isEqualTo(signatures)
    }

    @Test
    fun `find a filtered transaction after persisting a filtered transaction when a verified signed transaction exists filters the signed transaction`() {
        val signatures = createSignatures(Instant.now())
        val outputStates = defaultVisibleTransactionOutputs
        val signedTransaction = createSignedTransaction(outputStates = outputStates, signatures = signatures)
        val filteredTransaction = createFilteredTransaction(signedTransaction, indexes = listOf(1))
        val transactionReader = TestUtxoTransactionReader(
            signedTransaction,
            "account",
            VERIFIED,
            emptyList()
        )
        persistenceService.persistTransaction(transactionReader, emptyMap())
        persistenceService.persistFilteredTransactions(mapOf(filteredTransaction to signatures), "account")
        // Returns the filtered transaction here, which bypasses the signed transaction filtering
        val loadedFilteredTransactions = (persistenceService as UtxoPersistenceServiceImpl)
            .findFilteredTransactions(listOf(signedTransaction.id.toString()))
        assertThat(loadedFilteredTransactions).hasSize(1)
        assertThat(loadedFilteredTransactions[signedTransaction.id]?.first).isEqualTo(filteredTransaction)
        // Calling [findFilteredTransactionsAndSignatures] does hit the signed transaction filtering
        val foundFilteredTransactions = (persistenceService as UtxoPersistenceServiceImpl)
            .findFilteredTransactionsAndSignatures(listOf(StateRef(signedTransaction.id, 0), StateRef(signedTransaction.id, 1)))
        assertThat(foundFilteredTransactions).containsOnlyKeys(signedTransaction.id)
        val foundFilteredTransaction = foundFilteredTransactions[signedTransaction.id]?.first
        val filteredOutputStates = foundFilteredTransaction?.getComponentGroupContent(8)
        assertThat(foundFilteredTransaction).isNotEqualTo(filteredTransaction)
        assertThat(filteredOutputStates).hasSize(2)
        assertThat(filteredOutputStates?.get(0)?.first).isEqualTo(0)
        assertThat(filteredOutputStates?.get(0)?.second).isEqualTo(outputStates[0].toBytes())
        assertThat(filteredOutputStates?.get(1)?.first).isEqualTo(1)
        assertThat(filteredOutputStates?.get(1)?.second).isEqualTo(outputStates[1].toBytes())
        assertThat(foundFilteredTransactions[signedTransaction.id]?.second).isEqualTo(signatures)
    }

    @Suppress("LongParameterList")
    private fun createUtxoTokenMap(
        transactionReader: TestUtxoTransactionReader,
        tokenType: String,
        tokenSymbol: String,
        tokenTag: String,
        issuerHash: SecureHash,
        ownerHash: SecureHash,
        tokenAmount: BigDecimal
    ) =
        transactionReader.getVisibleStates().map {
            it.value.ref to UtxoToken(
                UtxoTokenPoolKey(
                    tokenType,
                    issuerHash,
                    tokenSymbol
                ),
                tokenAmount,
                UtxoTokenFilterFields(
                    tokenTag,
                    ownerHash
                )
            )
        }.toMap()

    private fun persistTransactionViaEntity(
        entityFactory: UtxoEntityFactory,
        status: TransactionStatus = UNVERIFIED,
        inputStateRefs: List<StateRef> = defaultInputStateRefs,
        referenceStateRefs: List<StateRef> = defaultReferenceStateRefs,
        isFiltered: Boolean = false
    ): SignedTransactionContainer {
        val signedTransaction = createSignedTransaction(inputStateRefs = inputStateRefs, referenceStateRefs = referenceStateRefs)
        entityManagerFactory.transaction { em ->
            em.persist(createTransactionEntity(entityFactory, signedTransaction, status = status, isFiltered = isFiltered))
        }
        return signedTransaction
    }

    @Suppress("LongParameterList")
    private fun createTransactionEntity(
        entityFactory: UtxoEntityFactory,
        signedTransaction: SignedTransactionContainer,
        account: String = "Account",
        createdTs: Instant = testClock.instant(),
        status: TransactionStatus = UNVERIFIED,
        isFiltered: Boolean = false
    ): Any {
        val metadataBytes = signedTransaction.wireTransaction.componentGroupLists[0][0]
        val metadata = entityFactory.createOrFindUtxoTransactionMetadataEntity(
            digest("SHA-256", metadataBytes).toString(),
            metadataBytes,
            "fakeGroupParametersHash",
            "fakeCpiFileChecksum"
        )

        return entityFactory.createUtxoTransactionEntity(
            signedTransaction.id.toString(),
            signedTransaction.wireTransaction.privacySalt.bytes,
            account,
            createdTs,
            status.value,
            createdTs,
            metadata,
            isFiltered
        ).also { transaction ->
            transaction.field<MutableCollection<Any>>("components").addAll(
                signedTransaction.wireTransaction.componentGroupLists.flatMapIndexed { groupIndex, componentGroup ->
                    componentGroup.mapIndexed { leafIndex: Int, component ->
                        if (groupIndex != 0 || leafIndex != 0) {
                            entityFactory.createUtxoTransactionComponentEntity(
                                transaction,
                                groupIndex,
                                leafIndex,
                                component,
                                digest("SHA-256", component).toString()
                            )
                        } else {
                            null
                        }
                    }.filterNotNull()
                }
            )
            transaction.field<MutableCollection<Any>>("signatures").addAll(
                signedTransaction.signatures.mapIndexed { index, signature ->
                    entityFactory.createUtxoTransactionSignatureEntity(
                        transaction,
                        index,
                        serializationService.serialize(signature).bytes,
                        signature.by.toString(),
                        createdTs
                    )
                }
            )
        }
    }

    /**
     * Checks the transaction status. [floorDateTime] should be the lowest value that is a valid
     * time for the next value of `updated` for the record. The function will verify that this
     * field is at least the floor time.
     */
    private fun assertTransactionStatus(
        transactionId: String,
        status: TransactionStatus,
        entityFactory: UtxoEntityFactory,
        floorDateTime: Instant
    ) {
        entityManagerFactory.transaction { em ->
            val dbTransaction = em.find(entityFactory.utxoTransaction, transactionId)
            assertAll(
                { assertThat(dbTransaction.field<String>("status")).isEqualTo(status.value) },
                { assertThat(dbTransaction.field<Instant>("updated")).isAfterOrEqualTo(floorDateTime) }
            )
        }
    }

    private fun createFilteredTransaction(
        signedTransaction: SignedTransactionContainer,
        indexes: List<Int> = emptyList()
    ): FilteredTransaction {
        val (outputInfoGroupParameter, outputGroupParameter) = if (indexes.isEmpty()) {
            ComponentGroupFilterParameters.AuditProof(
                UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                Any::class.java,
                ComponentGroupFilterParameters.AuditProof.AuditProofPredicate.Content { true }
            ) to ComponentGroupFilterParameters.AuditProof(
                UtxoComponentGroup.OUTPUTS.ordinal,
                Any::class.java,
                ComponentGroupFilterParameters.AuditProof.AuditProofPredicate.Content { true }
            )
        } else {
            ComponentGroupFilterParameters.AuditProof(
                UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                UtxoOutputInfoComponent::class.java,
                ComponentGroupFilterParameters.AuditProof.AuditProofPredicate.Index(indexes)
            ) to ComponentGroupFilterParameters.AuditProof(
                UtxoComponentGroup.OUTPUTS.ordinal,
                ContractState::class.java,
                ComponentGroupFilterParameters.AuditProof.AuditProofPredicate.Index(indexes)
            )
        }
        return filteredTransactionFactory.create(
            signedTransaction.wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(
                    METADATA.ordinal,
                    TransactionMetadata::class.java,
                    ComponentGroupFilterParameters.AuditProof.AuditProofPredicate.Content { true }
                ),
                ComponentGroupFilterParameters.AuditProof(
                    NOTARY.ordinal,
                    Any::class.java,
                    ComponentGroupFilterParameters.AuditProof.AuditProofPredicate.Content { true }
                ),
                outputInfoGroupParameter,
                outputGroupParameter,
            )
        )
    }

    private fun createSignedTransaction(
        seed: String = seedSequence.incrementAndGet().toString(),
        inputStateRefs: List<StateRef> = defaultInputStateRefs,
        referenceStateRefs: List<StateRef> = defaultReferenceStateRefs,
        outputStates: List<ContractState> = defaultVisibleTransactionOutputs,
        signatures: List<DigitalSignatureAndMetadata> = createSignatures()
    ): SignedTransactionContainer {
        val transactionMetadata = utxoTransactionMetadataExample(cpkPackageSeed = seed)
        val timeWindow = Instant.now().plusMillis(Duration.ofDays(1).toMillis())
        val componentGroupLists: List<List<ByteArray>> = listOf(
            listOf(jsonValidator.canonicalize(jsonMarshallingService.format(transactionMetadata)).toByteArray()),
            listOf(notaryExampleName.toBytes(), notaryExampleKey.toBytes(), timeWindow.toBytes()),
            listOf("group2_component1".toByteArray()),
            outputStates.map {
                UtxoOutputInfoComponent(
                    null,
                    null,
                    notaryExampleName,
                    notaryExampleKey,
                    it::class.java.name,
                    "contract tag"
                ).toBytes()
            },
            listOf("group4_component1".toByteArray()),
            listOf("group5_component1".toByteArray()),
            inputStateRefs.map { it.toBytes() },
            referenceStateRefs.map { it.toBytes() },
            outputStates.map { it.toBytes() },
            listOf("group9_component1".toByteArray())

        )
        val wireTransaction = wireTransactionFactory.create(
            componentGroupLists,
            getPrivacySalt()
        )

        return SignedTransactionContainer(wireTransaction, signatures)
    }

    private fun createSignatures(createdTs: Instant = testClock.instant()): List<DigitalSignatureAndMetadata> {
        val publicKey = KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair().public
        return listOf(getSignatureWithMetadataExample(publicKey, createdTs))
    }

    private class TestUtxoTransactionReader(
        val transactionContainer: SignedTransactionContainer,
        override val account: String,
        override val status: TransactionStatus,
        override val visibleStatesIndexes: List<Int>
    ) : UtxoTransactionReader {
        override val id: SecureHash
            get() = transactionContainer.id
        override val privacySalt: PrivacySalt
            get() = transactionContainer.wireTransaction.privacySalt
        override val metadata: TransactionMetadataInternal
            get() = transactionContainer.wireTransaction.metadata as TransactionMetadataInternal
        override val rawGroupLists: List<List<ByteArray>>
            get() = transactionContainer.wireTransaction.componentGroupLists
        override val signatures: List<DigitalSignatureAndMetadata>
            get() = transactionContainer.signatures
        override val cpkMetadata: List<CordaPackageSummary>
            get() = (transactionContainer.wireTransaction.metadata as TransactionMetadataInternal).getCpkMetadata()

        override fun getVisibleStates(): Map<Int, StateAndRef<ContractState>> {
            return mapOf(
                0 to stateAndRef<TestContract>(TestContractState1(), id, 0),
                1 to stateAndRef<TestContract>(TestContractState2(), id, 1)
            )
        }

        override fun getConsumedStates(persistenceService: UtxoPersistenceService): List<StateAndRef<ContractState>> {
            TODO("Not yet implemented")
        }

        override fun getReferenceStateRefs(): List<StateRef> {
            return listOf(StateRef(SecureHashImpl("SHA-256", ByteArray(34)), 2))
        }

        override fun getConsumedStateRefs(): List<StateRef> {
            return listOf(StateRef(SecureHashImpl("SHA-256", ByteArray(12)), 1))
        }

        private inline fun <reified C : Contract> stateAndRef(
            state: ContractState,
            transactionId: SecureHash,
            index: Int
        ): StateAndRef<ContractState> {
            return StateAndRefImpl(
                object : TransactionState<ContractState> {

                    override fun getContractState(): ContractState {
                        return state
                    }

                    override fun getContractStateType(): Class<ContractState> {
                        return state.javaClass
                    }

                    override fun getContractType(): Class<out Contract> {
                        return C::class.java
                    }

                    override fun getNotaryName(): MemberX500Name {
                        return notaryExampleName
                    }

                    override fun getNotaryKey(): PublicKey {
                        return publicKeyExample
                    }

                    override fun getEncumbranceGroup(): EncumbranceGroup? {
                        return null
                    }
                },

                StateRef(transactionId, index)
            )
        }
    }

    class TestContract : Contract {
        override fun verify(transaction: UtxoLedgerTransaction) {
        }
    }

    data class TestContractState1(val value: UUID = UUID.randomUUID()) : ContractState {
        override fun getParticipants(): List<PublicKey> {
            return emptyList()
        }
    }

    data class TestContractState2(val value: UUID = UUID.randomUUID()) : ContractState {
        override fun getParticipants(): List<PublicKey> {
            return emptyList()
        }
    }

    private fun ContractState.toBytes() = serializationService.serialize(this).bytes
    private fun StateRef.toBytes() = serializationService.serialize(this).bytes
    private fun UtxoOutputInfoComponent.toBytes() = serializationService.serialize(this).bytes
    private fun MemberX500Name.toBytes() = serializationService.serialize(this).bytes
    private fun PublicKey.toBytes() = serializationService.serialize(this).bytes
    private fun Instant.toBytes() = serializationService.serialize(this).bytes

    private fun digest(algorithm: String, data: ByteArray) =
        SecureHashImpl(algorithm, MessageDigest.getInstance(algorithm).digest(data))

    private fun nextTime() = testClock.peekTime()

    private fun newRandomSecureHash(): SecureHash {
        val random = Random()
        return SecureHashImpl(DigestAlgorithmName.SHA2_256.name, ByteArray(32).also(random::nextBytes))
    }

    private fun utxoTransactionMetadataExample(cpkPackageSeed: String? = null) = TransactionMetadataImpl(
        mapOf(
            TransactionMetadataImpl.LEDGER_MODEL_KEY to UtxoLedgerTransactionImpl::class.java.name,
            TransactionMetadataImpl.LEDGER_VERSION_KEY to UtxoTransactionMetadata.LEDGER_VERSION,
            TransactionMetadataImpl.TRANSACTION_SUBTYPE_KEY to UtxoTransactionMetadata.TransactionSubtype.GENERAL,
            TransactionMetadataImpl.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
            TransactionMetadataImpl.PLATFORM_VERSION_KEY to 123,
            TransactionMetadataImpl.CPI_METADATA_KEY to cpiPackageSummaryExample,
            TransactionMetadataImpl.CPK_METADATA_KEY to cpkPackageSummaryListExample(cpkPackageSeed),
            TransactionMetadataImpl.SCHEMA_VERSION_KEY to TransactionMetadataImpl.SCHEMA_VERSION,
            TransactionMetadataImpl.COMPONENT_GROUPS_KEY to utxoComponentGroupStructure,
            TransactionMetadataImpl.MEMBERSHIP_GROUP_PARAMETERS_HASH_KEY to "MEMBERSHIP_GROUP_PARAMETERS_HASH"
        )
    )
}

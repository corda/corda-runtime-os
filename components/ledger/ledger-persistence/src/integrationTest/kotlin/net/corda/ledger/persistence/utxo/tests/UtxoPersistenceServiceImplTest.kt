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
import net.corda.ledger.common.data.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.common.data.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
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
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.EncumbranceGroup
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
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
import java.util.concurrent.atomic.AtomicInteger
import javax.persistence.EntityManagerFactory
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.v5.ledger.utxo.observer.UtxoTokenFilterFields
import net.corda.v5.ledger.utxo.observer.UtxoTokenPoolKey

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
@Suppress("FunctionName")
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
    private val emConfig = DbUtils.getEntityManagerConfiguration("ledger_db_for_test")

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var currentSandboxGroupContext: CurrentSandboxGroupContext

    companion object {
        // Truncating to millis as on Windows builds the micros are lost after fetching the data from Postgres
        private const val TESTING_DATAMODEL_CPB = "/META-INF/testing-datamodel.cpb"
        private const val TIMEOUT_MILLIS = 10000L
        private val testClock = AutoTickTestClock(
            Instant.now().truncatedTo(ChronoUnit.MILLIS), Duration.ofSeconds(1)
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

            persistenceService = UtxoPersistenceServiceImpl(
                entityManagerFactory,
                repository,
                serializationService,
                digestService,
                factoryRegistry,
                DefaultContractStateVaultJsonFactoryImpl(),
                jsonMarshallingService,
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
    fun `find unconsumed visible transaction states`() {
        val createdTs = testClock.instant()
        val entityFactory = UtxoEntityFactory(entityManagerFactory)
        val transaction1 = createSignedTransaction(createdTs)
        val transaction2 = createSignedTransaction(createdTs)
        entityManagerFactory.transaction { em ->

            em.createNativeQuery("DELETE FROM {h-schema}utxo_visible_transaction_output").executeUpdate()

            createTransactionEntity(entityFactory, transaction1, status = VERIFIED).also { em.persist(it) }
            createTransactionEntity(entityFactory, transaction2, status = VERIFIED).also { em.persist(it) }

            repository.persistVisibleTransactionOutput(
                em,
                transaction1.id.toString(),
                UtxoComponentGroup.OUTPUTS.ordinal,
                1,
                ContractState::class.java.name,
                timestamp = createdTs,
                consumed = false,
                customRepresentation = CustomRepresentation("{}")
            )

            repository.persistVisibleTransactionOutput(
                em,
                transaction2.id.toString(),
                UtxoComponentGroup.OUTPUTS.ordinal,
                0,
                ContractState::class.java.name,
                timestamp = createdTs,
                consumed = false,
                customRepresentation = CustomRepresentation("{}")
            )

            repository.persistVisibleTransactionOutput(
                em,
                transaction2.id.toString(),
                UtxoComponentGroup.OUTPUTS.ordinal,
                1,
                ContractState::class.java.name,
                timestamp = createdTs,
                consumed = true,
                customRepresentation = CustomRepresentation("{}")
            )
        }

        val stateClass = TestContractState2::class.java
        val unconsumedStates = persistenceService.findUnconsumedVisibleStatesByType(stateClass)
        assertThat(unconsumedStates).isNotNull
        assertThat(unconsumedStates.size).isEqualTo(1)
        val visibleTransactionOutput = unconsumedStates.first()
        assertThat(visibleTransactionOutput.transactionId).isEqualTo(transaction1.id.toString())
        assertThat(visibleTransactionOutput.leafIndex).isEqualTo(1)
        assertThat(visibleTransactionOutput.info).isEqualTo(transaction1.wireTransaction.componentGroupLists[UtxoComponentGroup.OUTPUTS_INFO.ordinal][1])
        assertThat(visibleTransactionOutput.data).isEqualTo(transaction1.wireTransaction.componentGroupLists[UtxoComponentGroup.OUTPUTS.ordinal][1])
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
            assertThat(visibleTransactionOutput.info).isEqualTo(transactions[i].wireTransaction.componentGroupLists[UtxoComponentGroup.OUTPUTS_INFO.ordinal][i])
            assertThat(visibleTransactionOutput.data).isEqualTo(transactions[i].wireTransaction.componentGroupLists[UtxoComponentGroup.OUTPUTS.ordinal][i])
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
        val signedTransaction = createSignedTransaction(Instant.now())
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
            val txCreatedTs = dbTransaction.field<Instant?>("created")

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
                            assertThat(dbComponent.field<Int>("groupIndex")).isEqualTo(groupIndex +1 )
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

            val dbTransactionOutputs = em.createNamedQuery(
                "UtxoVisibleTransactionOutputEntity.findByTransactionId",
                entityFactory.utxoVisibleTransactionOutput
            )
                .setParameter("transactionId", signedTransaction.id.toString())
                .resultList
            assertThat(dbTransactionOutputs).isNotNull
                .hasSameSizeAs(componentGroupListsWithoutMetadata[UtxoComponentGroup.OUTPUTS.ordinal-1])
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

        assertThat(persistedSignedGroupParameters?.mgmSignature?.publicKey.toString()).isEqualTo(signedGroupParameters.mgmSignature?.publicKey.toString())
        assertThat(persistedSignedGroupParameters?.mgmSignatureSpec.toString()).isEqualTo(signedGroupParameters.mgmSignatureSpec.toString())
    }

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
        referenceStateRefs: List<StateRef> = defaultReferenceStateRefs

    ): SignedTransactionContainer {
        val signedTransaction = createSignedTransaction(inputStateRefs = inputStateRefs, referenceStateRefs = referenceStateRefs)
        entityManagerFactory.transaction { em ->
            em.persist(createTransactionEntity(entityFactory, signedTransaction, status = status))
        }
        return signedTransaction
    }

    private fun createTransactionEntity(
        entityFactory: UtxoEntityFactory,
        signedTransaction: SignedTransactionContainer,
        account: String = "Account",
        createdTs: Instant = testClock.instant(),
        status: TransactionStatus = UNVERIFIED
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
            metadata
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
        transactionId: String, status: TransactionStatus,
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

    private fun createSignedTransaction(
        createdTs: Instant = testClock.instant(),
        seed: String = seedSequence.incrementAndGet().toString(),
        inputStateRefs: List<StateRef> = defaultInputStateRefs,
        referenceStateRefs: List<StateRef> = defaultReferenceStateRefs
    ): SignedTransactionContainer {
        val transactionMetadata = utxoTransactionMetadataExample(cpkPackageSeed = seed,)
        val componentGroupLists: List<List<ByteArray>> = listOf(
            listOf(jsonValidator.canonicalize(jsonMarshallingService.format(transactionMetadata)).toByteArray()),
            listOf("group1_component1".toByteArray()),
            listOf("group2_component1".toByteArray()),
            listOf(
                UtxoOutputInfoComponent(
                    null, null, notaryExampleName, notaryExampleKey, TestContractState1::class.java.name, "contract tag"
                ).toBytes(),
                UtxoOutputInfoComponent(
                    null, null, notaryExampleName, notaryExampleKey, TestContractState2::class.java.name, "contract tag"
                ).toBytes()
            ),
            listOf("group4_component1".toByteArray()),
            listOf("group5_component1".toByteArray()),
            inputStateRefs.map { it.toBytes() },
            referenceStateRefs.map { it.toBytes() },
            defaultVisibleTransactionOutputs.map { it.toBytes() },
            listOf("group9_component1".toByteArray())

        )
        val wireTransaction = wireTransactionFactory.create(
            componentGroupLists,
            getPrivacySalt()
        )
        val publicKey = KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair().public
        val signatures = listOf(getSignatureWithMetadataExample(publicKey, createdTs))
        return SignedTransactionContainer(wireTransaction, signatures)
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

    class TestContractState1 : ContractState {
        override fun getParticipants(): List<PublicKey> {
            return emptyList()
        }
    }

    class TestContractState2 : ContractState {
        override fun getParticipants(): List<PublicKey> {
            return emptyList()
        }
    }

    private fun ContractState.toBytes() = serializationService.serialize(this).bytes
    private fun StateRef.toBytes() = serializationService.serialize(this).bytes
    private fun UtxoOutputInfoComponent.toBytes() = serializationService.serialize(this).bytes

    private fun digest(algorithm: String, data: ByteArray) =
        SecureHashImpl(algorithm, MessageDigest.getInstance(algorithm).digest(data))

    private fun nextTime() = testClock.peekTime()

    private fun newRandomSecureHash(): SecureHash {
        val random = Random()
        return SecureHashImpl(DigestAlgorithmName.SHA2_256.name, ByteArray(32).also(random::nextBytes))
    }

    private fun utxoTransactionMetadataExample(cpkPackageSeed: String? = null) = TransactionMetadataImpl(mapOf(
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
    ))
}

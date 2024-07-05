package net.corda.ledger.lib.dependencies.ledger


import net.corda.flow.application.crypto.DigitalSignatureVerificationServiceImpl
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactoryImpl
import net.corda.ledger.common.data.transaction.filtered.factory.impl.FilteredTransactionFactoryImpl
import net.corda.ledger.common.flow.impl.transaction.PrivacySaltProviderServiceImpl
import net.corda.ledger.common.flow.impl.transaction.TransactionSignatureServiceImpl
import net.corda.ledger.common.flow.impl.transaction.TransactionSignatureVerificationServiceImpl
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.cipherSchemeMetadata
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.digestService
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.merkleTreeProvider
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.signatureSpecService
import net.corda.ledger.lib.dependencies.crypto.CryptoDependencies.signatureVerificationService
import net.corda.ledger.lib.dependencies.db.DbDependencies.entityManagerFactory
import net.corda.ledger.lib.dependencies.json.JsonDependencies.jsonMarshallingService
import net.corda.ledger.lib.dependencies.json.JsonDependencies.jsonValidator
import net.corda.ledger.lib.dependencies.sandbox.SandboxDependencies.currentSandboxGroupContext
import net.corda.ledger.lib.dependencies.serialization.SerializationDependencies.serializationService
import net.corda.ledger.lib.dependencies.signing.SigningDependencies.layeredPropertyMapFactory
import net.corda.ledger.lib.dependencies.signing.SigningDependencies.signingService
import net.corda.ledger.lib.impl.stub.checkpoint.StubFlowCheckpointService
import net.corda.ledger.lib.impl.stub.external.event.VerificationExternalEventExecutor
import net.corda.ledger.lib.impl.stub.flow.StubFlowEngine
import net.corda.ledger.lib.impl.stub.groupparameters.StubGroupParametersCache
import net.corda.ledger.lib.impl.stub.groupparameters.StubGroupParametersLookup
import net.corda.ledger.lib.impl.stub.groupparameters.StubSignedGroupParametersVerifier
import net.corda.ledger.lib.impl.stub.ledger.StubStateAndRefCache
import net.corda.ledger.lib.impl.stub.ledger.StubUtxoLedgerGroupParametersPersistenceService
import net.corda.ledger.lib.impl.stub.ledger.StubUtxoLedgerStateQueryService
import net.corda.ledger.lib.impl.stub.platform.StubPlatformInfoProvider
import net.corda.ledger.lib.impl.stub.transaction.StubTransactionMetadataFactory
import net.corda.ledger.lib.impl.stub.transaction.StubUtxoLedgerTransactionFactory
import net.corda.ledger.lib.impl.stub.verification.StubUtxoLedgerTransactionVerificationService
import net.corda.ledger.persistence.utxo.impl.PostgresUtxoQueryProvider
import net.corda.ledger.persistence.utxo.impl.UtxoRepositoryImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.impl.UtxoLedgerTransactionFactoryImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.impl.UtxoSignedTransactionFactoryImpl
import net.corda.ledger.utxo.flow.impl.transaction.verifier.NotarySignatureVerificationServiceImpl
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationServiceImpl
import net.corda.membership.lib.impl.GroupParametersFactoryImpl
import net.corda.messagebus.kafka.serialization.CordaAvroSerializationFactoryImpl
import net.corda.orm.impl.DefaultDatabaseTypeProvider
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl

object LedgerDependencies {
    // ------------------------------------------------------------------------
    // ALL GOOD
    private val transactionSignatureVerificationServiceImpl = TransactionSignatureVerificationServiceImpl(
        serializationService,
        DigitalSignatureVerificationServiceImpl(signatureVerificationService),
        signatureSpecService,
        merkleTreeProvider,
        digestService,
        cipherSchemeMetadata
    )

    private val platformInfoProvider = StubPlatformInfoProvider()

    private val transactionSignatureServiceImpl = TransactionSignatureServiceImpl(
        serializationService,
        signingService,
        signatureSpecService,
        merkleTreeProvider,
        platformInfoProvider,
        StubFlowEngine(),
        transactionSignatureVerificationServiceImpl
    )

    private val wireTransactionFactory = WireTransactionFactoryImpl(
        merkleTreeProvider,
        digestService,
        jsonMarshallingService,
        jsonValidator
    )

    private val utxoQueryProvider = PostgresUtxoQueryProvider(DefaultDatabaseTypeProvider())
    val utxoRepo = UtxoRepositoryImpl(
        serializationService,
        wireTransactionFactory,
        utxoQueryProvider
    )

    private val stateAndRefCache = StubStateAndRefCache()
    private val groupParametersCache = StubGroupParametersCache()

    private val avroSchemaRegistry = AvroSchemaRegistryImpl()
    private val cordaAvro = CordaAvroSerializationFactoryImpl(avroSchemaRegistry)
    private val groupParametersFactory = GroupParametersFactoryImpl(
        layeredPropertyMapFactory,
        cipherSchemeMetadata,
        cordaAvro
    )

    private val utxoLedgerStateQueryService = StubUtxoLedgerStateQueryService(
        entityManagerFactory,
        serializationService,
        utxoRepo,
        stateAndRefCache
    )

    // ------------------------------------------------------------------------

    // TODO Might need to review the logic in this to pass verification
    private val groupParametersLookup = StubGroupParametersLookup()


    private val utxoLedgerGroupParametersPersistenceService = StubUtxoLedgerGroupParametersPersistenceService(
        entityManagerFactory,
        utxoRepo,
        groupParametersCache,
        groupParametersFactory,
        cipherSchemeMetadata
    )

    private val ledgerTxFactory = UtxoLedgerTransactionFactoryImpl(
        serializationService,
        utxoLedgerStateQueryService,
        utxoLedgerGroupParametersPersistenceService,
        groupParametersLookup
    )

    private val groupParamsVerifier = StubSignedGroupParametersVerifier()

    private val utxoLedgerTransactionVerificationService = UtxoLedgerTransactionVerificationServiceImpl(
        VerificationExternalEventExecutor(serializationService),
        serializationService,
        currentSandboxGroupContext,
        groupParamsVerifier
    )

    val utxoSignedTransactionFactoryImpl = UtxoSignedTransactionFactoryImpl(
        currentSandboxGroupContext, // OK
        jsonMarshallingService, // OK
        jsonValidator, // OK
        serializationService, // OK
        transactionSignatureServiceImpl, // OK
        StubTransactionMetadataFactory(), // OK - probably
        wireTransactionFactory, // OK
        ledgerTxFactory, // OK
        utxoLedgerTransactionVerificationService, // OK
        utxoLedgerGroupParametersPersistenceService, // OK
        groupParametersLookup, // OK - probably
        groupParamsVerifier, // OK - probably
        NotarySignatureVerificationServiceImpl(transactionSignatureVerificationServiceImpl), // OK
        PrivacySaltProviderServiceImpl(StubFlowCheckpointService()) // OK
    )

    val utxoLedgerTransactionFactory = UtxoLedgerTransactionFactoryImpl(
        serializationService,
        utxoLedgerStateQueryService,
        utxoLedgerGroupParametersPersistenceService,
        groupParametersLookup
    )

    val filteredTransactionFactory = FilteredTransactionFactoryImpl(
        jsonMarshallingService,
        jsonValidator,
        merkleTreeProvider,
        serializationService,
        digestService
    )
}
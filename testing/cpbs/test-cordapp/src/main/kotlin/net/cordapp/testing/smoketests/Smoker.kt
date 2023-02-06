package net.cordapp.testing.smoketests

import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.days
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.v5.ledger.utxo.observer.UtxoTokenFilterFields
import net.corda.v5.ledger.utxo.observer.UtxoTokenPoolKey
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.membership.MemberInfo
import net.cordapp.testing.bundles.dogs.Dog
import net.cordapp.testing.smoketests.flow.messages.InitiatedSmokeTestMessage
import org.slf4j.Logger
import java.math.BigDecimal
import java.security.PublicKey
import java.time.Instant
import java.util.*

class SmokerContract : Contract {
    override fun verify(transaction: UtxoLedgerTransaction) {
    }
}

@Suppress("LongParameterList")
@BelongsToContract(SmokerContract::class)
class SmokerState(
    val issuer: SecureHash,
    override val participants: List<PublicKey>
) : ContractState

class SmokerStateObserver : UtxoLedgerTokenStateObserver<SmokerState> {

    override val stateType = SmokerState::class.java

    override fun onCommit(state: SmokerState): UtxoToken {
        return UtxoToken(
            poolKey = UtxoTokenPoolKey(
                tokenType = SmokerState::class.java.name,
                issuerHash = state.issuer,
                symbol = "symbol"
            ),
            BigDecimal(100),
            filterFields = UtxoTokenFilterFields()
        )
    }
}

class SmokerRequest {
    var counterPartX500Name: String? = null
    var notaryX500Name: String? = null
}

data class SmokerResponse(
    val output: Map<String, String>
)

class SmokerMessage(val name: String)

@Suppress("unused", "TooManyFunctions")
@InitiatingFlow(protocol = "smoker-protocol")
class SmokerFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var digitalSignatureVerificationService: DigitalSignatureVerificationService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var serializationService: SerializationService

    @CordaInject
    lateinit var signingService: SigningService

    @CordaInject
    lateinit var memberLookupService: MemberLookup

    @CordaInject
    lateinit var signatureSpecService: SignatureSpecService

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        val request = requestBody.getRequestBodyAs<SmokerRequest>(jsonMarshallingService)


        val counterpartyX500Name = MemberX500Name.parse(
            checkNotNull(request.counterPartX500Name) { "Counterparty name can't be null" }
        )
        val notaryX500Name = MemberX500Name.parse(
            checkNotNull(request.notaryX500Name) { "Counterparty name can't be null" }
        )

        val recorder = Recorder(log)

        /**
         * 1 )
         * Check membership is working by getting my info and members info
         * for the counterparty and notary
         */
        var (myInfo, counterpartyInfo, notaryInfo) = recorder.record("Get Member Info") {
            listOf(
                memberLookupService.myInfo(),
                memberLookupService.lookup(counterpartyX500Name)!!,
                memberLookupService.lookup(notaryX500Name)!!
            )
        }

        /**
         * 2)
         * Check we can initiate a session with a counterparty
         */
        recorder.record("Session Send & Receive") {
            sessionTest(counterpartyX500Name)
        }

        /**
         * 3)
         * Persist a custom entity
         */
        recorder.record("Persist Entity") {
            persistEntity()
        }

        /**
         * 4)
         * Sign and verify a byte array
         */
        recorder.record("Sign & Verify") {
            signAndVerify(counterpartyInfo)
        }

        /**
         * 5)
         * Sign and verify a byte array
         */
        recorder.record("Sign & Verify") {
            signAndVerify(counterpartyInfo)
        }

        /**
         * 6)
         * Create Transaction
         */

        return jsonMarshallingService.format(SmokerResponse(recorder.records))
    }

    @Suspendable
    private fun sessionTest(counterpartyX500Name: MemberX500Name) {
        val session = flowMessaging.initiateFlow(counterpartyX500Name)
        session.sendAndReceive(SmokerMessage::class.java, SmokerMessage("hello"))
    }

    @Suspendable
    private fun persistEntity() {
        val dog = Dog(UUID.randomUUID(), "dog", Instant.now(), "none")
        persistenceService.persist(dog)
    }

    @Suspendable
    private fun signAndVerify(member: MemberInfo) {
        val publicKey = member.ledgerKeys[0]
        val bytesToSign = byteArrayOf(1, 2, 3, 4, 5)
        val signedBytes = signingService.sign(bytesToSign, publicKey, SignatureSpec.ECDSA_SHA256)
        digitalSignatureVerificationService.verify(
            publicKey,
            SignatureSpec.ECDSA_SHA256,
            signedBytes.bytes,
            bytesToSign
        )
    }

    @Suspendable
    private fun createAndFinalizeTransaction(me: MemberInfo, counterparty: MemberInfo) {
        val participants = listOf(me.ledgerKeys.first(), counterparty.ledgerKeys.first())

        CoinState(
            issuer = counterparty.name.toSecureHash(),
            currency = creationRequest.currency,
            value = BigDecimal(creationRequest.valueOfCoin),
            participants = participants,
            tag = creationRequest.tag,
            ownerHash = ownerHash
        )

        val notary = notaryLookup.notaryServices.first()

        log.info("Creating transaction...")
        val txBuilder = utxoLedgerService.getTransactionBuilder()

        @Suppress("DEPRECATION")
        val signedTransaction = txBuilder
            .setNotary(Party(notary.name, notary.publicKey))
            .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(1.days.toMillis()))
            .addOutputStates(coins)
            .addCommand(NullCoinCommand())
            .addSignatories(participants)
            .toSignedTransaction(me.ledgerKeys.first())

        val counterpartySession = flowMessaging.initiateFlow(counterparty.name)

        utxoLedgerService.finalize(
            signedTransaction,
            listOf(counterpartySession)
        )

    }

    private fun MemberX500Name.toSecureHash(): SecureHash {
        return SecureHash(
            DigestAlgorithmName.SHA2_256.name,
            this.toString().toByteArray()
        )
    }
}

class Recorder(private val log: Logger) {
    val records = mutableMapOf<String, String>()

    @Suspendable
    fun <T> record(name: String, block: () -> T): T {
        log.info("Starting - '$name'")
        val start = Instant.now()
        val result = block()
        records[name] = "Executed in ${Instant.now().toEpochMilli() - start.toEpochMilli()}ms"
        log.info("'$name' - ${records["name"]}")
        return result
    }
}

@InitiatedBy("smoker-protocol")
class SmokerResponderFlow : ResponderFlow {

    private companion object {
        val log = contextLogger()
    }

    @Suspendable
    override fun call(session: FlowSession) {
        val received = session.receive<InitiatedSmokeTestMessage>()
        session.send(InitiatedSmokeTestMessage(received.message))
    }
}
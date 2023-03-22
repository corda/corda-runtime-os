package net.cordacon.example.rollcall

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import org.slf4j.LoggerFactory
import java.security.PublicKey
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@InitiatedBy(protocol = "truancy-record")
class TruancyResponderFlow : ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var verificationService: DigitalSignatureVerificationService

    @CordaInject
    lateinit var serializationService: SerializationService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var digestService: DigestService

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("Request to process truancy records received")

        val record = session.receive(TruancyRecord::class.java)

        val knownKeysByIds = memberLookup.lookup().flatMap {
            it.ledgerKeys
        }.associateBy {
            it.fullIdHash(digestService)
        }
        val keyOfSignature = knownKeysByIds[record.signature.by]

        if (keyOfSignature == null) {
            log.info("Received signature from unknown member for key id: ${record.signature.by}")
            return
        }

        verificationService.verify(
            serializationService.serialize(record.absentees).bytes,
            record.signature.bytes,
            keyOfSignature,
            SignatureSpec.ECDSA_SHA256
        )
        log.info("Records verified; persisting records")

        persistenceService.persist(record.absentees.map { TruancyEntity(name = it.toString()) })
        log.info("Records persisted")
    }
}

@CordaSerializable
@Entity
@Table(name = "truancy_entity")
data class TruancyEntity(
    @Id
    @Column(name = "id")
    val id: UUID = UUID.randomUUID(),
    @Column(name = "student_name")
    val name: String
)

private fun PublicKey.fullIdHash(digestService: DigestService): SecureHash =
    digestService.hash(this.encoded, DigestAlgorithmName.SHA2_256)
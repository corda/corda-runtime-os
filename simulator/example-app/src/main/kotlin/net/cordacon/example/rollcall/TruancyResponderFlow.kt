package net.cordacon.example.rollcall

import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SignatureSpec
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@InitiatedBy("truancy-record")
class TruancyResponderFlow : ResponderFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var verificationService: DigitalSignatureVerificationService

    @CordaInject
    lateinit var serializationService: SerializationService

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("Request to process truancy records received")

        val record = session.receive(TruancyRecord::class.java)

        verificationService.verify(
            record.signature.by, SignatureSpec.ECDSA_SHA256, record.signature.bytes,
            serializationService.serialize(record.absentees).bytes
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

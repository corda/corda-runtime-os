package net.cordacon.example

import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SignatureSpec
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@InitiatedBy("truancy-record")
class TruancyResponderFlow : ResponderFlow {

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var verificationService: DigitalSignatureVerificationService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(session: FlowSession) {
        val record = session.receive(TruancyRecord::class.java).unwrap {it}

        verificationService.verify(record.signature.by, SignatureSpec.ECDSA_SHA256, record.signature.bytes,
            jsonMarshallingService.format(record.absentees.map{it.toString()}).toByteArray())

        persistenceService.persist(record.absentees.map { TruancyEntity(name = it.toString()) })
    }

}

@CordaSerializable
@Entity
data class TruancyEntity(
    @Id
    @Column
    val id: UUID = UUID.randomUUID(),
    @Column
    val name: String
)

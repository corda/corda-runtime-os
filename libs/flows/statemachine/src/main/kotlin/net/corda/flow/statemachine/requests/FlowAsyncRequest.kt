package net.corda.flow.statemachine.requests

import co.paralleluniverse.strands.Strand
import net.corda.flow.statemachine.FlowIORequest
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.v5.application.services.crypto.KeyManagementService
import net.corda.v5.application.services.persistence.PersistenceService
import net.corda.v5.base.util.uncheckedCast
import java.security.PublicKey

interface FlowAsyncRequest<REQUEST : Any, RESPONSE : Any> : FlowIORequest<RESPONSE> {

    val type: String
    val to: String
    val payload: REQUEST // avro object

    fun response(obj: Any): RESPONSE // cast the incoming avro object to specific avro type
}

// wraps all output events so it can hold flow events and events to other topics
class OutputEvent(val key: Any, val to: String, val payload: Any)

// avro object
// response represents a union type in the FlowAsyncResponse.avsc file for the different type of async requests
class FlowAsyncResponse(val response: Any)

// persistence example

//@Component(service = [InjectableFactory::class])
class PersistenceServiceFactory(private val sandboxAwarePersistenceService: SandboxAwarePersistenceService) :
    InjectableFactory<PersistenceService> {

    override val target = PersistenceService::class.java

    override fun create(fiber: FlowStateMachine<*>, sandboxGroup: SandboxGroup): PersistenceService {
        return PeristenceServiceImpl(sandboxAwarePersistenceService, sandboxGroup)
    }
}

interface SandboxAwarePersistenceService {

    fun <T : Any> find(
        sandboxGroup: SandboxGroup,
        entityClass: Class<T>,
        primaryKey: Any
    ): FlowAsyncRequest<AvroPersistenceFindRequest, AvroPersistenceFindResponse>

    fun <T : Any> find(sandboxGroup: SandboxGroup, response: AvroPersistenceFindResponse): T?
}

class AvroPersistenceFindRequest(val entityClass: Class<*>, val primaryKey: Any)
class AvroPersistenceFindResponse(val response: Any?)

// @Component
class SandboxAwarePersistenceServiceImpl : SandboxAwarePersistenceService {

    private companion object {
        const val TYPE = "PERSISTENCE"
    }

    // set by the services component
    var to: String = "database-worker-topic"

    override fun <T : Any> find(
        sandboxGroup: SandboxGroup,
        entityClass: Class<T>,
        primaryKey: Any
    ): FlowAsyncRequest<AvroPersistenceFindRequest, AvroPersistenceFindResponse> {
        return PersistenceFindAsyncRequest(to, entityClass, primaryKey)
    }

    class PersistenceFindAsyncRequest(
        override val to: String,
        entityClass: Class<*>,
        primaryKey: Any
    ) : FlowAsyncRequest<AvroPersistenceFindRequest, AvroPersistenceFindResponse> {

        override val type: String = TYPE

        override val payload: AvroPersistenceFindRequest = AvroPersistenceFindRequest(entityClass, primaryKey)

        override fun response(obj: Any): AvroPersistenceFindResponse {
            return requireNotNull(obj as? AvroPersistenceFindResponse) { "Wrong response was received" }
        }
    }

    override fun <T : Any> find(sandboxGroup: SandboxGroup, response: AvroPersistenceFindResponse): T? {
        // probably just remove the generics off this interface
        return uncheckedCast(response.response)
    }
}

class PeristenceServiceImpl(
    private val sandboxAwarePersistenceService: SandboxAwarePersistenceService,
    private val sandboxGroup: SandboxGroup
) : PersistenceService {

    override fun <T : Any> find(entityClass: Class<T>, primaryKey: Any): T? {
        val request = sandboxAwarePersistenceService.find(sandboxGroup, entityClass, primaryKey)
        val response = (Strand.currentStrand() as FlowStateMachine<*>).suspend(request)
        return sandboxAwarePersistenceService.find(sandboxGroup, response)
    }
}

// signing example

//@Component(service = [InjectableFactory::class])
class KeyManagementServiceFactory(private val sandboxAwareKeyManagementService: SandboxAwareKeyManagementService) :
    InjectableFactory<KeyManagementService> {

    override val target = KeyManagementService::class.java

    override fun create(fiber: FlowStateMachine<*>, sandboxGroup: SandboxGroup): KeyManagementService {
        return KeyManagementServiceImpl(sandboxAwareKeyManagementService, sandboxGroup)
    }
}

interface SandboxAwareKeyManagementService {

    fun sign(sandboxGroup: SandboxGroup, bytes: ByteArray, publicKey: PublicKey): DigitalSignature.WithKey

    fun sign(sandboxGroup: SandboxGroup, response: AvroSigningResponse): DigitalSignature.WithKey
}

class AvroSigningRequest(val bytes: ByteArray, val publicKey: PublicKey)
class AvroSigningResponse(val response: Any?)

// @Component
class SandboxAwareKeyManagementServiceImpl : SandboxAwareKeyManagementService {

    private companion object {
        const val TYPE = "SIGNING"
    }

    // set by the services component
    var to: String = "signing-worker-topic"

    override fun sign(
        sandboxGroup: SandboxGroup,
        bytes: ByteArray,
        publicKey: PublicKey
    ): FlowAsyncRequest<AvroSigningRequest, AvroSigningResponse> {
        return SigningAsyncRequest(to, bytes, publicKey)
    }

    class SigningAsyncRequest(
        override val to: String,
        bytes: ByteArray,
        publicKey: PublicKey
    ) : FlowAsyncRequest<AvroSigningRequest, AvroSigningResponse> {

        override val type: String = TYPE

        override val payload: AvroSigningRequest = AvroSigningRequest(bytes, publicKey)

        override fun response(obj: Any): AvroSigningResponse {
            return requireNotNull(obj as? AvroSigningResponse) { "Wrong response was received" }
        }
    }

    override fun sign(sandboxGroup: SandboxGroup, response: AvroSigningResponse): DigitalSignature.WithKey {
        // probably just remove the generics off this interface
        return uncheckedCast(response.response)
    }
}

class KeyManagementServiceImpl(
    private val sandboxAwareKeyManagementService: SandboxAwareKeyManagementService,
    private val sandboxGroup: SandboxGroup
) : KeyManagementService {

    override fun sign(bytes: ByteArray, publicKey: PublicKey): DigitalSignature.WithKey {
        val request = sandboxAwareKeyManagementService.sign(sandboxGroup, bytes, publicKey)
        val response = (Strand.currentStrand() as FlowStateMachine<*>).suspend(request)
        return sandboxAwareKeyManagementService.sign(sandboxGroup, response)
    }
}
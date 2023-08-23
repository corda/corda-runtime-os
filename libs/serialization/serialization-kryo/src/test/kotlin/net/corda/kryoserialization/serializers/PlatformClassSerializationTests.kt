package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.crypto.cipher.suite.CustomSignatureSpec
import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.flow.application.serialization.SerializationServiceInternal
import net.corda.flow.application.sessions.impl.FlowInfoImpl
import net.corda.flow.application.sessions.impl.FlowSessionImpl
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.state.FlowContext
import net.corda.kryoserialization.DefaultKryoCustomizer
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.messaging.FlowInfo
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.bytes
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Instant

class PlatformClassSerializationTests {
    private val mockFlowFiberService = mock<FlowFiberService>()
    private val serializationService = mock<SerializationServiceInternal>()
    private val flowContext = mock<FlowContext>()
    private val signatureSpec = mock<CustomSignatureSpec>()
    private val digitalSignature = mock<DigitalSignatureWithKeyId>()
    private val digitalsignatureMetadata = DigitalSignatureMetadata(Instant.now(), signatureSpec, mapOf("key" to "Value"))
    @Test
    fun `FlowInfo serialization test`() {
        val output = Output(100)
        val kryo = Kryo()
        DefaultKryoCustomizer.customize(
            kryo,
            emptyMap(),
            ClassSerializer(mock())
        )
        kryo.register(FlowInfo::class.java)

        val info: FlowInfo = FlowInfoImpl("test", 1)

        kryo.writeClassAndObject(output, info)
        val tested = kryo.readClassAndObject(Input(output.buffer)) as FlowInfo

        Assertions.assertThat(tested.protocol()).isEqualTo(info.protocol())
        Assertions.assertThat(tested.protocolVersion()).isEqualTo(info.protocolVersion())

    }

    @Test
    fun `DigitalSignatureMetadata serialization test`() {
        val output = Output(10000)
        val kryo = Kryo()
        DefaultKryoCustomizer.customize(
            kryo,
            emptyMap(),
            ClassSerializer(mock())
        )
        kryo.register(FlowInfo::class.java)

        val signatureMeta = digitalsignatureMetadata

        kryo.writeClassAndObject(output, signatureMeta)
        val tested = kryo.readClassAndObject(Input(output.buffer)) as DigitalSignatureMetadata

        Assertions.assertThat(tested.properties).isEqualTo(signatureMeta.properties)
        Assertions.assertThat(tested.timestamp).isEqualTo(signatureMeta.timestamp)
    }

    @Disabled
    @Test
    fun `FlowSession serialization test`() {
        val output = Output(1000)
        val kryo = Kryo()
        DefaultKryoCustomizer.customize(
            kryo,
            emptyMap(),
            ClassSerializer(mock())
        )
        kryo.register(FlowSession::class.java)
        val counterParty = MemberX500Name("Alice", "Alice Corp", "LDN", "GB")

        val flowSession: FlowSession = FlowSessionImpl(counterParty, "SessionId1", mockFlowFiberService, serializationService, flowContext, FlowSessionImpl.Direction.INITIATING_SIDE)

        kryo.writeClassAndObject(output, flowSession)
        val tested = kryo.readClassAndObject(Input(output.buffer)) as FlowSession

        Assertions.assertThat(tested.contextProperties).isEqualTo(flowSession.contextProperties)
    }
    @Test
    fun `DigitalSignatureAndMetadata serialization test`() {
        val output = Output(10000)
        val kryo = Kryo()
        DefaultKryoCustomizer.customize(
            kryo,
            emptyMap(),
            ClassSerializer(mock())
        )
        kryo.register(FlowInfo::class.java)

        val signatureAndMeta = DigitalSignatureAndMetadata(digitalSignature, digitalsignatureMetadata)

        kryo.writeClassAndObject(output, signatureAndMeta)
        val tested = kryo.readClassAndObject(Input(output.buffer)) as DigitalSignatureAndMetadata

        Assertions.assertThat(tested.signature).isEqualTo(signatureAndMeta.signature)
        Assertions.assertThat(tested.metadata).isEqualTo(signatureAndMeta.metadata)
    }
    @Test
    fun `InitiatedBy serialization test`() {
        val output = Output(10000)
        val kryo = Kryo()
        DefaultKryoCustomizer.customize(
            kryo,
            emptyMap(),
            ClassSerializer(mock())
        )
        kryo.register(InitiatedBy::class.java)

        val initiatedBy = InitiatedBy(protocol="testingSerialization", version = intArrayOf(1234))

        kryo.writeClassAndObject(output, initiatedBy)
        val tested = kryo.readClassAndObject(Input(output.buffer)) as InitiatedBy

        Assertions.assertThat(tested).isEqualTo(initiatedBy)
        Assertions.assertThat(tested.protocol).isEqualTo(initiatedBy.protocol)
        Assertions.assertThat(tested.version).isEqualTo(initiatedBy.version)
    }
    @Test
    fun `InitiatingFlow serialization test`() {
        val output = Output(10000)
        val kryo = Kryo()
        DefaultKryoCustomizer.customize(
            kryo,
            emptyMap(),
            ClassSerializer(mock())
        )
        kryo.register(InitiatingFlow::class.java)

        val initiatingFlow = InitiatingFlow(protocol="testingSerialization", version = intArrayOf(1234))

        kryo.writeClassAndObject(output, initiatingFlow)
        val tested = kryo.readClassAndObject(Input(output.buffer)) as InitiatingFlow

        Assertions.assertThat(tested).isEqualTo(initiatingFlow)
        Assertions.assertThat(tested.protocol).isEqualTo(initiatingFlow.protocol)
        Assertions.assertThat(tested.version).isEqualTo(initiatingFlow.version)
    }
    @Test
    fun `SecureHash serialization test`() {
        val output = Output(10000)
        val kryo = Kryo()
        DefaultKryoCustomizer.customize(
            kryo,
            emptyMap(),
            ClassSerializer(mock())
        )
        kryo.register(SecureHash::class.java)

        val hash: SecureHash = SecureHashImpl("TestAlgorithmName", "1234".toByteArray())

        kryo.writeClassAndObject(output, hash)
        val tested = kryo.readClassAndObject(Input(output.buffer)) as SecureHash

        Assertions.assertThat(tested).isEqualTo(hash)
        Assertions.assertThat(tested.algorithm).isEqualTo(hash.algorithm)
        Assertions.assertThat(tested.bytes).isEqualTo(hash.bytes)
    }
    @Test
    fun `SignatureSpec serialization test`() {
        val output = Output(10000)
        val kryo = Kryo()
        DefaultKryoCustomizer.customize(
            kryo,
            emptyMap(),
            ClassSerializer(mock())
        )
        kryo.register(SignatureSpec::class.java)

        val sigSpec: SignatureSpec = SignatureSpecImpl("TestSignatureName")

        kryo.writeClassAndObject(output, sigSpec)
        val tested = kryo.readClassAndObject(Input(output.buffer)) as SignatureSpec

        Assertions.assertThat(tested).isEqualTo(sigSpec)
        Assertions.assertThat(tested.signatureName).isEqualTo(sigSpec.signatureName)
    }
    @Test
    fun `DigestAlgorithmName serialization test`() {
        val output = Output(10000)
        val kryo = Kryo()
        DefaultKryoCustomizer.customize(
            kryo,
            emptyMap(),
            ClassSerializer(mock())
        )
        kryo.register(DigestAlgorithmName::class.java)

        val digestAlgorithmName = DigestAlgorithmName("TestName")

        kryo.writeClassAndObject(output, digestAlgorithmName)
        val tested = kryo.readClassAndObject(Input(output.buffer)) as DigestAlgorithmName

        Assertions.assertThat(tested).isEqualTo(digestAlgorithmName)
        Assertions.assertThat(tested.name).isEqualTo(digestAlgorithmName.name)
    }
    @Test
    fun `CordaRuntimeException serialization test`() {
        val output = Output(10000)
        val kryo = Kryo()
        DefaultKryoCustomizer.customize(
            kryo,
            emptyMap(),
            ClassSerializer(mock())
        )
        kryo.register(CordaRuntimeException::class.java)

        val cause = Throwable("TestException")

        val exception = CordaRuntimeException("TestClassName", "Test Exception Message", cause)

        kryo.writeClassAndObject(output, exception)
        val tested = kryo.readClassAndObject(Input(output.buffer)) as CordaRuntimeException

        Assertions.assertThat(tested.originalExceptionClassName).isEqualTo(exception.originalExceptionClassName)
        Assertions.assertThat(tested.message).isEqualTo(exception.message)
        Assertions.assertThat(tested.cause.toString()).isEqualTo(exception.cause.toString())
    }
}

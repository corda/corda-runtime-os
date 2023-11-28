package net.corda.internal.serialization.amqp.custom

import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert
import org.junit.jupiter.api.Test

class ParameterizedSignatureSpecTest {
    @Test
    fun `ParameterizedSignatureSpec taking PSSParameterSpec as AlgorithmParameterSpec is serializable`() {
        val parameterizedSignatureSpec = SignatureSpecs.RSASSA_PSS_SHA256
        ReusableSerialiseDeserializeAssert.serializeDeserializeAssert(parameterizedSignatureSpec)
    }
}

package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert
import net.corda.v5.crypto.SignatureSpec
import org.junit.jupiter.api.Test

class ParameterizedSignatureSpecTest {
    @Test
    fun `ParameterizedSignatureSpec taking PSSParameterSpec as AlgorithmParameterSpec is serializable`() {
        val parameterizedSignatureSpec = SignatureSpec.RSASSA_PSS_SHA256
        ReusableSerialiseDeserializeAssert.serializeDeserializeAssert(parameterizedSignatureSpec)
    }
}
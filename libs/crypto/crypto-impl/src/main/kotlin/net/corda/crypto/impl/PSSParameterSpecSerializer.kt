package net.corda.crypto.impl

import net.corda.crypto.cipher.suite.schemes.AlgorithmParameterSpecSerializer
import java.nio.ByteBuffer
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

class PSSParameterSpecSerializer : AlgorithmParameterSpecSerializer<PSSParameterSpec> {
    companion object {
        const val MGF1 = "MGF1"
    }

    override fun serialize(params: PSSParameterSpec): ByteArray {
        require(params.mgfParameters is MGF1ParameterSpec) {
            "Supports only '${MGF1ParameterSpec::class.java}'"
        }
        val digestAlgorithm = params.digestAlgorithm.toByteArray()
        val mgfParameters = (params.mgfParameters as MGF1ParameterSpec).digestAlgorithm.toByteArray()
        val buffer = ByteBuffer.allocate(
            4 + digestAlgorithm.size +
                    4 + mgfParameters.size +
                    4 +
                    4
        )
        buffer.putInt(digestAlgorithm.size)
        buffer.put(digestAlgorithm)
        buffer.putInt(mgfParameters.size)
        buffer.put(mgfParameters)
        buffer.putInt(params.saltLength)
        buffer.putInt(params.trailerField)
        return buffer.array()
    }

    override fun deserialize(bytes: ByteArray): PSSParameterSpec {
        val buffer = ByteBuffer.wrap(bytes)
        val digestAlgorithm = ByteArray(buffer.int)
        buffer.get(digestAlgorithm)
        val mgfParameters = ByteArray(buffer.int)
        buffer.get(mgfParameters)
        val saltLength = buffer.int
        val trailerField = buffer.int
        return PSSParameterSpec(
            String(digestAlgorithm),
            MGF1,
            MGF1ParameterSpec(String(mgfParameters)),
            saltLength,
            trailerField
        )
    }
}
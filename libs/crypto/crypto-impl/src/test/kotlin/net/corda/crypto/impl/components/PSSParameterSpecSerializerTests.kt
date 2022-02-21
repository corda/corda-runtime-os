package net.corda.crypto.impl.components

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import kotlin.test.assertEquals

class PSSParameterSpecSerializerTests {
    class TestParams(
        val digestAlgorithm: String,
        val mgfParameters: String,
        val saltLength: Int,
        val trailerField: Int
    )

    companion object {
        @JvmStatic
        fun params(): Array<TestParams> = arrayOf(
            TestParams("SHA-256", "SHA-256", 32, 1),
            TestParams("SHA-512", "SHA-256", 32, 1),
            TestParams("SHA-512", "SHA-512", 64, 1),
            TestParams("SHA-384", "SHA-384", 48, 1),
            TestParams("SHA-384", "SHA-512", 48, 4)
        )
    }

    @ParameterizedTest
    @MethodSource("params")
    @Timeout(60)
    fun `Should round trip serialize and deserialize`(testParams: TestParams) {
        val params = PSSParameterSpec(
            testParams.digestAlgorithm,
            "MGF1",
            MGF1ParameterSpec(testParams.mgfParameters),
            testParams.saltLength,
            testParams.trailerField
        )
        val serializer = PSSParameterSpecSerializer()
        val bytes = serializer.serialize(params)
        val result = serializer.deserialize(bytes)
        assertEquals(testParams.digestAlgorithm, result.digestAlgorithm)
        assertEquals("MGF1", result.mgfAlgorithm)
        assertEquals(testParams.mgfParameters, (result.mgfParameters as MGF1ParameterSpec).digestAlgorithm)
        assertEquals(testParams.saltLength, result.saltLength)
        assertEquals(testParams.trailerField, result.trailerField)
    }

    @Test
    @Timeout(60)
    fun `Should throw IllegalArgumentException to serialize params with non MGF1ParameterSpec`() {
        val params = PSSParameterSpec(
            "SHA-256",
            "MGF1",
            mock(),
            32,
            1
        )
        val serializer = PSSParameterSpecSerializer()
        assertThrows<IllegalArgumentException> {
            serializer.serialize(params)
        }
    }
}
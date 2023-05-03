package com.r3.corda.testing.smoketests.flow.digest

import net.corda.v5.crypto.extensions.DigestAlgorithm
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory
import java.io.InputStream

class CustomDigestFactory : DigestAlgorithmFactory {
    override fun getAlgorithm(): String =
        CustomDigestAlgorithm.algorithmName

    override fun getInstance(): DigestAlgorithm =
        TODO("Not yet implemented")
}

class CustomDigestAlgorithm : DigestAlgorithm {
    companion object {
        const val algorithmName = "CUSTOM_DIGEST"
    }

    override fun getAlgorithm(): String {
        TODO("Not yet implemented")
    }

    override fun getDigestLength(): Int {
        TODO("Not yet implemented")
    }

    override fun digest(bytes: ByteArray): ByteArray {
        TODO("Not yet implemented")
    }

    override fun digest(inputStream: InputStream): ByteArray {
        TODO("Not yet implemented")
    }

}
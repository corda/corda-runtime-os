package net.corda.p2p.fake.ca

import org.bouncycastle.jce.ECNamedCurveTable
import java.security.InvalidAlgorithmParameterException
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class ValidCurvedNames : Iterable<String> {
    private fun validName(name: String): Boolean {
        val kpg = KeyPairGenerator.getInstance("EC")
        val parameter = ECGenParameterSpec(name)
        return try {
            kpg.initialize(parameter)
            true
        } catch (e: InvalidAlgorithmParameterException) {
            false
        }
    }
    override fun iterator(): Iterator<String> {
        return ECNamedCurveTable.getNames()
            .toList()
            .filterIsInstance<String>()
            .filter { validName(it) }
            .iterator()
    }
}

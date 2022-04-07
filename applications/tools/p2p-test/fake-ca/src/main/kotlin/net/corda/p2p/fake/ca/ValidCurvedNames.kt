package net.corda.p2p.fake.ca

import org.bouncycastle.jce.ECNamedCurveTable
import java.security.InvalidAlgorithmParameterException
import java.security.KeyPairGenerator
import java.security.Security
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
        val disabled = Security.getProperty("jdk.disabled.namedCurves")
            .split(",").map {
                it.trim()
            }.toSet()

        return ECNamedCurveTable.getNames()
            .toList()
            .filterIsInstance<String>()
            .filter {
                !disabled.contains(it)
            }.filter { validName(it) }
            .iterator()
    }
}

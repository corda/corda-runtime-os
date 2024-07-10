package net.corda.ledger.lib.impl.stub.groupparameters

import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.lib.keyPairExample
import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.membership.NotaryInfo
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Instant

class StubSignedGroupParameters : SignedGroupParameters {
    override val hash: SecureHash
        get() = SecureHashImpl("SHA-256", ByteArray(12))
    override val mgmSignature: DigitalSignatureWithKey
        get() = DigitalSignatureWithKey(keyPairExample.public, byteArrayOf(42))
    override val mgmSignatureSpec: SignatureSpec
        get() = SignatureSpecImpl("NONE")
    override val groupParameters: ByteArray
        get() = ByteArray(0)

    override fun getEntries(): MutableSet<MutableMap.MutableEntry<String, String>> {
        return mutableSetOf()
    }

    override fun get(key: String): String? {
        TODO("Not yet implemented")
    }

    override fun <T : Any> parse(key: String, clazz: Class<out T>): T {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> parseOrNull(key: String, clazz: Class<out T>): T? {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> parseList(itemKeyPrefix: String, clazz: Class<out T>): MutableList<T> {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> parseSet(itemKeyPrefix: String, clazz: Class<out T>): MutableSet<T> {
        TODO("Not yet implemented")
    }

    override fun getModifiedTime(): Instant {
        return Instant.now()
    }

    override fun getEpoch(): Int {
        return 1
    }

    override fun getNotaries(): MutableCollection<NotaryInfo> {
        return mutableListOf(object : NotaryInfo {
            override fun getName() = MemberX500Name("Alice", "Alice Corp", "LDN", "GB")
            override fun getProtocol() = "whatever"
            override fun getProtocolVersions() = mutableListOf(1)
            override fun getPublicKey() = keyPairExample.public
            override fun isBackchainRequired() = false
        })
    }
}

private val kpg = KeyPairGenerator.getInstance("EC")
    .apply { initialize(ECGenParameterSpec("secp256r1")) }

val keyPairExample: KeyPair = kpg.generateKeyPair()
package net.corda.ledger.lib.impl.stub.groupparameters

import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.SecureHashImpl
import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.membership.NotaryInfo
import java.time.Instant

class StubSignedGroupParameters : SignedGroupParameters {
    override val hash: SecureHash
        get() = SecureHashImpl("SHA-256", ByteArray(12))






    override val mgmSignature: DigitalSignatureWithKey
        get() = TODO("Not yet implemented")
    override val mgmSignatureSpec: SignatureSpec
        get() = TODO("Not yet implemented")
    override val groupParameters: ByteArray
        get() = TODO("Not yet implemented")

    override fun getEntries(): MutableSet<MutableMap.MutableEntry<String, String>> {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    override fun getEpoch(): Int {
        TODO("Not yet implemented")
    }

    override fun getNotaries(): MutableCollection<NotaryInfo> {
        TODO("Not yet implemented")
    }
}
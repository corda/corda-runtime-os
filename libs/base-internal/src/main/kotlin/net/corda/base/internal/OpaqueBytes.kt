package net.corda.base.internal

import net.corda.v5.base.annotations.CordaSerializable

/**
 * A simple class that wraps a byte array and makes the equals/hashCode/toString methods work as you actually expect.
 * In an ideal JVM this would be a value type and be completely overhead free. Project Valhalla is adding such
 * functionality to Java, but it won't arrive for a few years yet!
 */
@CordaSerializable
open class OpaqueBytes(private val bytes: ByteArray) : ByteSequence(bytes, 0, bytes.size) {
    companion object {
        /**
         * Create [OpaqueBytes] from a sequence of [Byte] values.
         */
        @JvmStatic
        fun of(vararg b: Byte) = OpaqueBytes(byteArrayOf(*b))
    }

    init {
        require(bytes.isNotEmpty()) { "Byte Array must not be empty" }
    }

    /**
     * The bytes are always cloned so that this object becomes immutable. This has been done
     * to prevent tampering with entities such as [net.corda.v5.crypto.SecureHash] and
     * [net.corda.v5.ledger.common.transactions.PrivacySaltImpl], as well as preserve the integrity
     * of our hash constants [net.corda.v5.crypto.DigestServiceUtils.getZeroHash] and
     * [net.corda.v5.crypto.DigestServiceUtils.getAllOnesHash].
     *
     * Cloning like this may become a performance issue, depending on whether or not the JIT
     * compiler is ever able to optimise away the clone. In which case we may need to revisit
     * this later.
     */
    override fun getBytes() = bytes.clone()
}

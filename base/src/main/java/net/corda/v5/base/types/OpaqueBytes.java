package net.corda.v5.base.types;

import net.corda.v5.base.annotations.CordaSerializable;
import org.jetbrains.annotations.NotNull;

/**
 * A simple class that wraps a byte array and makes the equals/hashCode/toString methods work as you actually expect.
 * In an ideal JVM this would be a value type and be completely overhead free. Project Valhalla is adding such
 * functionality to Java, but it won't arrive for a few years yet!
 */
@CordaSerializable
public class OpaqueBytes extends ByteSequence {
    private final byte[] bytes;

    /**
     * Create {@link OpaqueBytes} from a sequence of {@code byte} values.
     */
    @NotNull
    public static OpaqueBytes of(@NotNull byte... b) {
        return new OpaqueBytes(b);
    }

    public OpaqueBytes(@NotNull byte[] bytes) {
        super(bytes, 0, bytes.length);
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Byte Array must not be empty");
        }
        this.bytes = bytes;
    }

    /**
     * The bytes are always cloned so that this object becomes immutable. This has been done
     * to prevent tampering with entities such as {@link net.corda.v5.SecureHash} and
     * {@link net.corda.v5.ledger.common.transactions.PrivacySaltImpl}, as well as
     * preserve the integrity of our hash constants {@link net.corda.v5.crypto.DigestServiceUtils#getZeroHash} and
     * {@link net.corda.v5.crypto.DigestServiceUtils#getAllOnesHash}.
     * <p>
     * Cloning like this may become a performance issue, depending on whether or not the JIT
     * compiler is ever able to optimise away the clone. In which case we may need to revisit
     * this later.
     */
    @Override
    @NotNull
    public final byte[] getBytes() {
        return bytes.clone();
    }
}

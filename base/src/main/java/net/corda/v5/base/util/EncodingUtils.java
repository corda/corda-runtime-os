package net.corda.v5.base.util;

import net.corda.v5.base.types.ByteArrays;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.util.Base64;

// This file includes useful encoding methods and extension functions for the most common encoding/decoding operations.

public final class EncodingUtils {
    private EncodingUtils() {}

    // byte[] encoders

    /**
     * Convert a byte array to a Base58 encoded {@link String}.
     */
    @NotNull
    public static String toBase64(@NotNull byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Convert a byte array to a Base64 encoded {@link String}.
     */
    @NotNull
    public static String toBase58(@NotNull byte[] bytes) {
        return Base58.encode(bytes);
    }

    /**
     * Convert a byte array to a hex (Base16) capitalized encoded {@link String}.
     */
    @NotNull
    public static String toHex(@NotNull byte[] bytes) {
        return ByteArrays.toHexString(bytes);
    }

    // String encoders and decoders

    /**
     * Base58-String to the actual real {@link String}, i.e. "JxF12TrwUP45BMd" -> "Hello World".
     */
    @NotNull
    public static String base58ToRealString(@NotNull String encoded) {
        return new String(base58ToByteArray(encoded), Charset.defaultCharset());
    }

    /**
     * Base64-String to the actual real {@link String}, i.e. "SGVsbG8gV29ybGQ=" -> "Hello World".
     */
    @NotNull
    public static String base64ToRealString(@NotNull String encoded) {
        return new String(base64ToByteArray(encoded));
    }

    /**
     * HEX-String to the actual real {@link String}, i.e. "48656C6C6F20576F726C64" -> "Hello World".
     */
    @NotNull
    public static String hexToRealString(@NotNull String encoded) {
        return new String(hexToByteArray(encoded));
    }

    @NotNull
    public static byte[] base58ToByteArray(@NotNull String encoded) {
        return Base58.decode(encoded);
    }

    @NotNull
    public static byte[] base64ToByteArray(@NotNull String encoded) {
        return Base64.getDecoder().decode(encoded);
    }

    /**
     * Hex-{@link String} to byte[]. Accept any hex form (capitalized, lowercase, mixed).
     */
    @NotNull
    public static byte[] hexToByteArray(@NotNull String encoded) {
        return ByteArrays.parseAsHex(encoded);
    }

// Encoding changers

    /**
     * Encoding changer. Base58-{@link String} to Base64-{@link String}, i.e. "SGVsbG8gV29ybGQ=" -> JxF12TrwUP45BMd"
     */
    @NotNull
    public static String base58toBase64(@NotNull String input) {
        return toBase64(base58ToByteArray(input));
    }

    /**
     * Encoding changer. Base58-{@link String} to Hex-{@link String}, i.e. "SGVsbG8gV29ybGQ=" -> "48656C6C6F20576F726C64"
     */
    @NotNull
    public static String base58toHex(@NotNull String input) {
        return toHex(base58ToByteArray(input));
    }

    /**
     * Encoding changer. Base64-{@link String} to Base58-{@link String}, i.e. "SGVsbG8gV29ybGQ=" -> JxF12TrwUP45BMd"
     */
    @NotNull
    public static String base64toBase58(@NotNull String input) {
        return toBase58(base64ToByteArray(input));
    }

    /**
     * Encoding changer. Base64-{@link String} to Hex-{@link String}, i.e. "SGVsbG8gV29ybGQ=" -> "48656C6C6F20576F726C64"
     */
    @NotNull
    public static String base64toHex(@NotNull String input) {
        return toHex(base64ToByteArray(input));
    }

    /**
     * Encoding changer. Hex-{@link String} to Base58-{@link String}, i.e. "48656C6C6F20576F726C64" -> "JxF12TrwUP45BMd"
     */
    @NotNull
    public static String hexToBase58(@NotNull String input) {
        return toBase58(hexToByteArray(input));
    }

    /**
     * Encoding changer. Hex-{@link String} to Base64-{@link String}, i.e. "48656C6C6F20576F726C64" -> "SGVsbG8gV29ybGQ="
     */
    @NotNull
    public static String hexToBase64(@NotNull String input) {
        return toBase64(hexToByteArray(input));
    }

}

package net.corda.v5.base.util;

import org.jetbrains.annotations.NotNull;

public final class ByteArrays {
    private ByteArrays() {}

    /**
     * Converts this {@code byte[]} into a {@link String} of hexadecimal digits.
     */
    @NotNull
    public static String toHexString(@NotNull byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes may not be null");
        }
        return printHexBinary(bytes);
    }

    private static final char[] hexCode = "0123456789ABCDEF".toCharArray();

    @NotNull
    private static String printHexBinary(@NotNull byte[] data) {
        StringBuilder r = new StringBuilder(data.length * 2);
        for (byte b: data) {
            r.append(hexCode[(Byte.toUnsignedInt(b) >>> 4) & 0xF]);
            r.append(hexCode[Byte.toUnsignedInt(b) & 0xF]);
        }
        return r.toString();
    }

    /**
     * Converts this {@link String} of hexadecimal digits into a {@code byte[]}.
     * @throws IllegalArgumentException if the {@link String} contains incorrectly-encoded characters.
     */
    @NotNull
    public static byte[] parseAsHex(@NotNull String str) {
        if (str == null) {
            throw new IllegalArgumentException("str may not be null");
        }
        return parseHexBinary(str);
    }

    @NotNull
    private static byte[] parseHexBinary(@NotNull String s) {
        int len = s.length();

        // "111" is not a valid hex encoding.
        if (len % 2 != 0) {
            throw new IllegalArgumentException("hexBinary needs to be even-length: " + s);
        }

        byte[] out = new byte[len / 2];

        var i = 0;
        while (i < len) {
            int h = hexToBin(s.charAt(i));
            int l = hexToBin(s.charAt(i + 1));
            if (h == -1 || l == -1) {
                throw new IllegalArgumentException("contains illegal character for hexBinary: " + s);
            }

            out[i / 2] = (byte) (h * 16 + l);
            i += 2;
        }

        return out;
    }

    private static int hexToBin(char ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        }
        if (ch >= 'A' && ch <= 'F') {
            return ch - 'A' + 10;
        }
        return (ch >= 'a' && ch <= 'f') ? ch - 'a' + 10 : -1;
    }
}

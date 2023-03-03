package net.corda.v5.crypto;

import org.jetbrains.annotations.NotNull;

public final class KeySchemeCodes {
    private KeySchemeCodes() {}

    /**
     * RSA key scheme code name.
     * The key scheme can be used for signing only.
     */
    @NotNull
    public static final String RSA_CODE_NAME = "CORDA.RSA";

    /**
     * ECDSA with SECP256K1 curve key scheme code name.
     * The key scheme can be used for signing and key derivation such as ECDH.
     */
    @NotNull
    public static final String ECDSA_SECP256K1_CODE_NAME = "CORDA.ECDSA.SECP256K1";

    /**
     * ECDSA with SECP256R1 curve key scheme code name.
     * The key scheme can be used for signing and key derivation such as ECDH.
     */
    @NotNull
    public static final String ECDSA_SECP256R1_CODE_NAME = "CORDA.ECDSA.SECP256R1";

    /**
     * EdDSA with 25519PH curve key scheme code name.
     * The key scheme can be used for signing only.
     */
    @NotNull
    public static final String EDDSA_ED25519_CODE_NAME = "CORDA.EDDSA.ED25519";

    /**
     * EdDSA with X25519 curve key scheme code name.
     * The key scheme can be used for key derivation such as ECDH only.
     */
    @NotNull
    public static final String X25519_CODE_NAME = "CORDA.X25519";

    /**
     * SM2 key scheme code name.
     * As the key scheme is variant of ECDSA it can be used for signing and key derivation such as ECDH.
     */
    @NotNull
    public static final String SM2_CODE_NAME = "CORDA.SM2";

    /**
     * GOST3410 with GOST3411 key scheme code name.
     * The key scheme can be used for signing only.
     */
    @NotNull
    public static final String GOST3410_GOST3411_CODE_NAME = "CORDA.GOST3410.GOST3411";

    /**
     * SPHINCS post quantum key scheme code name.
     * The key scheme can be used for signing only.
     */
    @NotNull
    public static final String SPHINCS256_CODE_NAME = "CORDA.SPHINCS-256";

    /**
     * Composite Key, see [CompositeKey] for details.
     * The scheme cannot be directly used for signing or key derivation.
     */
    @NotNull
    public static final String COMPOSITE_KEY_CODE_NAME = "COMPOSITE";
}

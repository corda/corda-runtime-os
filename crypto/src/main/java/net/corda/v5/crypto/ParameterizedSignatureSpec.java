package net.corda.v5.crypto;

import org.jetbrains.annotations.NotNull;

import java.security.spec.AlgorithmParameterSpec;

/**
 * This class is used to define a digital signature scheme which has the additional algorithm parameters,
 * such as <code>RSASSA-PSS</code>.
 */
public final class ParameterizedSignatureSpec extends SignatureSpec {
    private final AlgorithmParameterSpec params;

    /**
     * Construct a parameterized signature spec.
     *
     * @param signatureName A signature-scheme name as required to create {@link java.security.Signature}
     *                      objects (e.g. <code>SHA256withECDSA</code>)
     * @param params        Signature parameters. For example, if using <code>RSASSA-PSS</code>, to avoid
     *                      using the default SHA1, you must specify the signature parameters explicitly.
     *                      <p>
     *                      When used for signing the <code>signatureName</code> must match the corresponding key scheme,
     *                      e.g. you cannot use <code>SHA256withECDSA<code> with <code>RSA</code> keys.
     */
    public ParameterizedSignatureSpec(@NotNull String signatureName, @NotNull AlgorithmParameterSpec params) {
        super(signatureName);
        this.params = params;
    }

    /**
     * Converts a {@link ParameterizedSignatureSpec} object to a string representation.
     * 
     * @return string representation
     */
    @NotNull
    public String toString() {
        return this.getSignatureName() + ':' + this.params.getClass().getSimpleName();
    }

    @NotNull
    public AlgorithmParameterSpec getParams() {
        return this.params;
    }
}

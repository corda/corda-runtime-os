package net.corda.cli.plugins.packaging.aws.kms.ec;

import net.corda.cli.plugins.packaging.aws.kms.KmsKey;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECParameterSpec;
import java.util.Objects;

public class KmsECPrivateKey implements KmsKey, ECPrivateKey {

    @NotNull
    private final String id;
    private final String algorithm = "EC";
    private final String format = "PKCS#8";

    public KmsECPrivateKey(String id) {
        Objects.requireNonNull(id);
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getAlgorithm() {
        return algorithm;
    }

    @Override
    public String getFormat() {
        return format;
    }

    @Override
    public BigInteger getS() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getEncoded() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ECParameterSpec getParams() {
        throw new UnsupportedOperationException();
    }

}

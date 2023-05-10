package net.corda.cli.plugins.packaging.aws.kms.rsa;

import net.corda.cli.plugins.packaging.aws.kms.KmsKey;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.security.interfaces.RSAPrivateKey;
import java.util.Objects;

public class KmsRSAPrivateKey implements KmsKey, RSAPrivateKey {

    @NotNull
    private final String id;
    private final String algorithm = "RSA";
    private final String format = "X.509";

    public KmsRSAPrivateKey(String id) {
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
    public BigInteger getPrivateExponent() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getEncoded() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigInteger getModulus() {
        throw new UnsupportedOperationException();
    }

}

package net.corda.cli.plugins.packaging.aws.kms.rsa;

import net.corda.cli.plugins.packaging.aws.kms.KmsPublicKey;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Objects;

public class KmsRSAPublicKey implements KmsPublicKey, RSAPublicKey {

    @NotNull
    private final String id;
    private final RSAPublicKey publicKey;

    public KmsRSAPublicKey(String id, RSAPublicKey publicKey) {
        Objects.requireNonNull(id);
        this.id = id;
        this.publicKey = publicKey;
    }

    public String getId() {
        return id;
    }

    @Override
    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    @Override
    public BigInteger getPublicExponent() {
        return publicKey.getPublicExponent();
    }

    @Override
    public String getAlgorithm() {
        return publicKey.getAlgorithm();
    }

    @Override
    public String getFormat() {
        return publicKey.getFormat();
    }

    @Override
    public byte[] getEncoded() {
        return publicKey.getEncoded();
    }

    @Override
    public BigInteger getModulus() {
        return publicKey.getModulus();
    }

}

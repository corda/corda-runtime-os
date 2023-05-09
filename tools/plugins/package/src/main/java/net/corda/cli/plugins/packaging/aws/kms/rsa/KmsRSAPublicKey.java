package net.corda.cli.plugins.packaging.aws.kms.rsa;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.corda.cli.plugins.packaging.aws.kms.KmsPublicKey;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class KmsRSAPublicKey implements KmsPublicKey, RSAPublicKey {

    @NonNull
    private final String id;
    private final RSAPublicKey publicKey;

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

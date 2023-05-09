package net.corda.cli.plugins.packaging.aws.kms.ec;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.corda.cli.plugins.packaging.aws.kms.KmsKey;

import java.math.BigInteger;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECParameterSpec;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class KmsECPrivateKey implements KmsKey, ECPrivateKey {

    @NonNull
    private final String id;
    private final String algorithm = "EC";
    private final String format = "PKCS#8";

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

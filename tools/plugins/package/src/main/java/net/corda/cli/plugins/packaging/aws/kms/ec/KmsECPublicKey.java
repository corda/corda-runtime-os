package net.corda.cli.plugins.packaging.aws.kms.ec;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.kms.jce.provider.KmsPublicKey;

import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class KmsECPublicKey implements KmsPublicKey, ECPublicKey {

    @NonNull
    private final String id;
    private final ECPublicKey publicKey;

    public String getId() {
        return id;
    }

    @Override
    public ECPublicKey getPublicKey() {
        return publicKey;
    }

    @Override
    public ECPoint getW() {
        return publicKey.getW();
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
    public ECParameterSpec getParams() {
        return publicKey.getParams();
    }

}

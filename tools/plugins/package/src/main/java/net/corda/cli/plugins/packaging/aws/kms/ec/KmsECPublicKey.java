package net.corda.cli.plugins.packaging.aws.kms.ec;

import net.corda.cli.plugins.packaging.aws.kms.KmsPublicKey;
import org.jetbrains.annotations.NotNull;

import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.util.Objects;

public class KmsECPublicKey implements KmsPublicKey, ECPublicKey {

    @NotNull
    private final String id;
    private final ECPublicKey publicKey;

    public KmsECPublicKey(String id, ECPublicKey publicKey) {
        Objects.requireNonNull(id);
        this.id = id;
        this.publicKey = publicKey;
    }

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

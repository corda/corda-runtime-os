package software.amazon.awssdk.services.kms.jce.provider.ec;

import software.amazon.awssdk.services.kms.jce.provider.KmsPublicKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class KmsECPublicKey implements KmsPublicKey, ECPublicKey {

    @NonNull
    private final String id;
    private final ECPublicKey publicKey;

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

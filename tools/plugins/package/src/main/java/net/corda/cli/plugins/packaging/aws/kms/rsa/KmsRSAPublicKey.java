package software.amazon.awssdk.services.kms.jce.provider.rsa;

import software.amazon.awssdk.services.kms.jce.provider.KmsPublicKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class KmsRSAPublicKey implements KmsPublicKey, RSAPublicKey {

    @NonNull
    private final String id;
    private final RSAPublicKey publicKey;

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

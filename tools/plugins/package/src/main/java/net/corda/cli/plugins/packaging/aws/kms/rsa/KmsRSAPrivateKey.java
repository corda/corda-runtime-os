package net.corda.cli.plugins.packaging.aws.kms.rsa;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.kms.jce.provider.KmsKey;

import java.math.BigInteger;
import java.security.interfaces.RSAPrivateKey;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class KmsRSAPrivateKey implements KmsKey, RSAPrivateKey {

    @NonNull
    private final String id;
    private final String algorithm = "RSA";
    private final String format = "X.509";

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

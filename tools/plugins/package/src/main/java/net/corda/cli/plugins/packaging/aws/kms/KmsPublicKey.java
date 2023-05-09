package software.amazon.awssdk.services.kms.jce.provider;

import java.security.PublicKey;

public interface KmsPublicKey extends KmsKey {

    PublicKey getPublicKey();

}

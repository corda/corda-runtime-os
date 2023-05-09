package software.amazon.awssdk.services.kms.jce.provider;

import java.security.Key;

public interface KmsKey extends Key {

    String getId();

}

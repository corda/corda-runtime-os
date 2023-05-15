package net.corda.cli.plugins.packaging.aws.kms;

import java.security.PublicKey;

public interface KmsPublicKey extends KmsKey {

    PublicKey getPublicKey();

}

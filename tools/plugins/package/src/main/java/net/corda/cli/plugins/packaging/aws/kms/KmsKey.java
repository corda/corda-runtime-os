package net.corda.cli.plugins.packaging.aws.kms;

import java.security.Key;

public interface KmsKey extends Key {

    String getId();

}

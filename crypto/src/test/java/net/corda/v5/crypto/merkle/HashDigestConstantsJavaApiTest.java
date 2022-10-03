package net.corda.v5.crypto.merkle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HashDigestConstantsJavaApiTest {
    @Test
    public void constantTests() {
        assertNotNull(HashDigestConstants.HASH_DIGEST_PROVIDER_DEFAULT_NAME);
        assertNotNull(HashDigestConstants.HASH_DIGEST_PROVIDER_NONCE_NAME);
        assertNotNull(HashDigestConstants.HASH_DIGEST_PROVIDER_NONCE_VERIFY_NAME);
        assertNotNull(HashDigestConstants.HASH_DIGEST_PROVIDER_NONCE_SIZE_ONLY_VERIFY_NAME);
        assertNotNull(HashDigestConstants.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME);
        assertNotNull(HashDigestConstants.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION);
        assertNotNull(HashDigestConstants.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION);
        assertNotNull(HashDigestConstants.HASH_DIGEST_PROVIDER_ENTROPY_OPTION);
    }
}

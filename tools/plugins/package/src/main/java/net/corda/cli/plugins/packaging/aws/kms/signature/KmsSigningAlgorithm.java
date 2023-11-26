package net.corda.cli.plugins.packaging.aws.kms.signature;

import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

public enum KmsSigningAlgorithm {

    RSASSA_PSS_SHA_256("RSASSA-PSS/SHA256", "SHA-256", SigningAlgorithmSpec.RSASSA_PSS_SHA_256),
    RSASSA_PSS_SHA_384("RSASSA-PSS/SHA384", "SHA-384", SigningAlgorithmSpec.RSASSA_PSS_SHA_384),
    RSASSA_PSS_SHA_512("RSASSA-PSS/SHA512", "SHA-512", SigningAlgorithmSpec.RSASSA_PSS_SHA_512),

    RSASSA_PKCS1_V1_5_SHA_256("SHA256withRSA", "SHA-256", SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256),
    RSASSA_PKCS1_V1_5_SHA_384("SHA384withRSA", "SHA-384", SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_384),
    RSASSA_PKCS1_V1_5_SHA_512("SHA512withRSA", "SHA-512", SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_512),

    ECDSA_SHA_256("SHA256withECDSA", "SHA-256", SigningAlgorithmSpec.ECDSA_SHA_256),
    ECDSA_SHA_384("SHA384withECDSA", "SHA-384", SigningAlgorithmSpec.ECDSA_SHA_384),
    ECDSA_SHA_512("SHA512withECDSA", "SHA-512", SigningAlgorithmSpec.ECDSA_SHA_512);

    private final String algorithm;
    private final String digestAlgorithm;
    private final SigningAlgorithmSpec signingAlgorithmSpec;

    KmsSigningAlgorithm(String algorithm, String digestAlgorithm, SigningAlgorithmSpec signingAlgorithmSpec) {
        this.algorithm = algorithm;
        this.digestAlgorithm = digestAlgorithm;
        this.signingAlgorithmSpec = signingAlgorithmSpec;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    public SigningAlgorithmSpec getSigningAlgorithmSpec() {
        return signingAlgorithmSpec;
    }
}
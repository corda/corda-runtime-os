package net.corda.cli.plugins.packaging

import net.corda.cli.plugins.packaging.aws.kms.ec.KmsECKeyFactory
import net.corda.cli.plugins.packaging.aws.kms.rsa.KmsRSAKeyFactory
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest
import java.security.PrivateKey


fun getKmsClient(): KmsClient {
    val region: Region = Region.EU_WEST_2
    return KmsClient.builder()
        .region(region)
        .credentialsProvider(ProfileCredentialsProvider.create())
        .build()
}

fun getRSAPrivateKey(keyId: String): PrivateKey {
    return KmsRSAKeyFactory.getPrivateKey(keyId)
}

fun getECPrivateKey(keyId: String): PrivateKey {
    return KmsECKeyFactory.getPrivateKey(keyId)
}

fun closeKmsClient(kmsClient: KmsClient) {
    kmsClient.close()
}

fun isRsaKeyType(kmsClient: KmsClient, keyId: String): Boolean {
    val describeKeyResponse = kmsClient.describeKey { builder: DescribeKeyRequest.Builder -> builder.keyId(keyId) }
    check(describeKeyResponse.keyMetadata().hasSigningAlgorithms()) { "Unsupported Key type. Only signing keys are supported." }

    return describeKeyResponse.keyMetadata().signingAlgorithmsAsStrings().stream().filter { a: String ->
        a.startsWith("RSA")
    }.findAny().isPresent
}

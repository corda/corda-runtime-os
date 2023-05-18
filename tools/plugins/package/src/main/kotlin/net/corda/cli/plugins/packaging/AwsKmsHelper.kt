package net.corda.cli.plugins.packaging

import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest

fun getKmsClient(): KmsClient = KmsClient.builder().build()

fun isRsaKeyType(kmsClient: KmsClient, keyId: String): Boolean {
    val describeKeyResponse = kmsClient.describeKey { builder: DescribeKeyRequest.Builder -> builder.keyId(keyId) }
    check(describeKeyResponse.keyMetadata().hasSigningAlgorithms()) { "Unsupported Key type. Only signing keys are supported." }

    return describeKeyResponse.keyMetadata().signingAlgorithmsAsStrings().stream().filter { a: String ->
        a.startsWith("RSA")
    }.findAny().isPresent
}

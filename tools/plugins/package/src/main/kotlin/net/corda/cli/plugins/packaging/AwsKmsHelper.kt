package net.corda.cli.plugins.packaging

import net.corda.cli.plugins.packaging.aws.kms.ec.KmsECKeyFactory
import net.corda.cli.plugins.packaging.aws.kms.rsa.KmsRSAKeyFactory
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest
import software.amazon.awssdk.services.kms.model.KeyListEntry
import software.amazon.awssdk.services.kms.model.KmsException
import software.amazon.awssdk.services.kms.model.ListKeysRequest
import software.amazon.awssdk.services.kms.model.ListKeysResponse
import java.security.PrivateKey
import kotlin.system.exitProcess


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

fun listAllKeys(kmsClient: KmsClient) {
    try {
        val listKeysRequest: ListKeysRequest = ListKeysRequest.builder()
            .limit(15)
            .build()
        val keysResponse: ListKeysResponse = kmsClient.listKeys(listKeysRequest)
        val keyListEntries: List<KeyListEntry> = keysResponse.keys()
        for (key in keyListEntries) {
            println("The key ARN is: " + key.keyArn())
            println("The key Id is: " + key.keyId())
        }
    } catch (e: KmsException) {
        System.err.println(e.message)
        exitProcess(1)
    }
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

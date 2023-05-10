package net.corda.cli.plugins.packaging

import net.corda.cli.plugins.packaging.aws.kms.rsa.KmsRSAKeyFactory
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.KeyListEntry
import software.amazon.awssdk.services.kms.model.KmsException
import software.amazon.awssdk.services.kms.model.ListKeysRequest
import software.amazon.awssdk.services.kms.model.ListKeysResponse
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate


fun getKmsClient(): KmsClient {
    val region: Region = Region.EU_WEST_2
    return KmsClient.builder()
        .region(region)
        .credentialsProvider(ProfileCredentialsProvider.create())
        .build()
}

fun getPrivateKey(keyId: String): PrivateKey {
    return KmsRSAKeyFactory.getPrivateKey(keyId)

}

fun listAllKeys(kmsClient: KmsClient) {
    try {
        val listKeysRequest: ListKeysRequest = ListKeysRequest.builder()
            .limit(15)
            .build()
        val keysResponse: ListKeysResponse = kmsClient.listKeys(listKeysRequest)
        val keyListEntries: List<KeyListEntry> = keysResponse.keys()
        for (key in keyListEntries) {
            System.out.println("The key ARN is: " + key.keyArn())
            System.out.println("The key Id is: " + key.keyId())
        }
    } catch (e: KmsException) {
        System.err.println(e.message)
        System.exit(1)
    }
}

fun closeKmsClient(kmsClient: KmsClient) {
    kmsClient.close()
}

@Throws(Exception::class)
fun convertStringToX509Cert(certificate: String): X509Certificate {
    val targetStream: InputStream = ByteArrayInputStream(certificate.toByteArray())
    return CertificateFactory
        .getInstance("X509")
        .generateCertificate(targetStream) as X509Certificate
}

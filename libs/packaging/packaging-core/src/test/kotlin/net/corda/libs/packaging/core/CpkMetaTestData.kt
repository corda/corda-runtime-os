package net.corda.libs.packaging.core

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Random

object CpkMetaTestData {
    val random = Random(0)

    // Need to truncate the timestamp as round trip to Avro wipes nanos
    val currentTimeStamp = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    val cpiId = CpiIdentifier(
        "SomeName",
        "1.0",
        SecureHash(DigestAlgorithmName.SHA2_256.name, ByteArray(32).also(random::nextBytes))
    )
    val cpkId = CpkIdentifier(
        "SomeName",
        "1.0", SecureHash(DigestAlgorithmName.SHA2_256.name, ByteArray(32).also(random::nextBytes))
    )
    val cpkDependencyId = CpkIdentifier(
        "SomeName 2",
        "1.0", SecureHash(DigestAlgorithmName.SHA2_256.name, ByteArray(32).also(random::nextBytes))
    )
    val cpkType = CpkType.CORDA_API
    val cpkFormatVersion = CpkFormatVersion(2, 3)
    val cpkManifest = CpkManifest(CpkFormatVersion(2, 3))
    val cordappType = CordappType.WORKFLOW
    val cordappManifest = CordappManifest(
        "net.cordapp.Bundle",
        "1.2.3",
        12,
        34,
        CordappType.WORKFLOW,
        "someName",
        "R3",
        42,
        "some license",
        mapOf(
            "Corda-Contract-Classes" to "contractClass1, contractClass2",
            "Corda-Flow-Classes" to "flowClass1, flowClass2"
        ),
    )

    private const val externalChannelsConfig = """
                {
                    "channel 1" : {
                        "type" : "send"
                    },
                    "channel 2" :{
                        "type" : "send-receive"
                    }
                }
            """

    fun create(): CpkMetadata {
        return CpkMetadata(
            cpkId,
            cpkManifest,
            "mainBundle.jar",
            listOf("library.jar"),
            cordappManifest,
            cpkType,
            SecureHash(DigestAlgorithmName.SHA2_256.name, ByteArray(32).also(random::nextBytes)),
            emptySet(),
            currentTimeStamp,
            externalChannelsConfig
        )
    }
}

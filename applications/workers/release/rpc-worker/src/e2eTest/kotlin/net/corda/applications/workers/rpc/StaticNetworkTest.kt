package net.corda.applications.workers.rpc

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.applications.workers.rpc.kafka.KafkaTestToolKit
import net.corda.data.identity.HoldingIdentity
import net.corda.httprpc.HttpFileUpload
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRPCOps
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeRequest
import net.corda.membership.httprpc.v1.MemberLookupRpcOps
import net.corda.membership.httprpc.v1.MemberRegistrationRpcOps
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.test.util.eventually
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes.Name.MANIFEST_VERSION
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

class StaticNetworkTest {
    private val testToolkit by TestToolkitProperty()
    private val kafkaToolKit by lazy {
        KafkaTestToolKit(testToolkit)
    }
    private val json = ObjectMapper()
    private fun createCertificate() = StaticNetworkTest::class.java.classLoader.getResource("certificate.pem")!!.readText()
    private val groupId = testToolkit.uniqueName

    private fun createGroupPolicyJson(
        memberNames: Collection<String>,
    ): ByteArray {
        val groupPolicy = mapOf(
            "fileFormatVersion" to 1,
            "groupId" to groupId,
            "registrationProtocol" to "net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService",
            "synchronisationProtocol" to "net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService",
            "protocolParameters" to mapOf(
                "sessionKeyPolicy" to "Combined",
                "staticNetwork" to mapOf(
                    "members" to
                        memberNames.map {
                            mapOf(
                                "name" to it,
                                "memberStatus" to "ACTIVE",
                                "endpointUrl-1" to "http://localhost:1080",
                                "endpointProtocol-1" to 1
                            )
                        }
                )
            ),
            "p2pParameters" to mapOf(
                "sessionTrustRoots" to listOf(
                    createCertificate(),
                    createCertificate()
                ),
                "tlsTrustRoots" to listOf(
                    createCertificate()
                ),
                "sessionPki" to "Standard",
                "tlsPki" to "Standard",
                "tlsVersion" to "1.3",
                "protocolMode" to "Authentication_Encryption"
            ),
            "cipherSuite" to mapOf(
                "corda.provider" to "default",
                "corda.signature.provider" to "default",
                "corda.signature.default" to "ECDSA_SECP256K1_SHA256",
                "corda.signature.FRESH_KEYS" to "ECDSA_SECP256K1_SHA256",
                "corda.digest.default" to "SHA256",
                "corda.cryptoservice.provider" to "default"
            )
        )

        return ByteArrayOutputStream().use { outputStream ->
            json.writeValue(outputStream, groupPolicy)

            outputStream.toByteArray()
        }
    }

    private val cordaVersion by lazy {
        val manifest = MemberRegistrationRpcOps::class.java.classLoader
            .getResource("META-INF/MANIFEST.MF")
            ?.openStream()
            ?.use {
                Manifest(it)
            }
        manifest?.mainAttributes?.getValue("Bundle-Version") ?: "5.0.0.0-SNAPSHOT"
    }

    private fun createEmptyJarWithManifest(membersNames: Collection<String>): ByteArray {
        return ByteArrayOutputStream().use { outputStream ->
            val manifest = Manifest()
            manifest.mainAttributes[MANIFEST_VERSION] = "1.0"
            manifest.mainAttributes.putValue("Corda-CPB-Name", testToolkit.uniqueName)
            manifest.mainAttributes.putValue("Corda-CPB-Version", cordaVersion)

            JarOutputStream(outputStream, manifest).use { jarOutputStream ->
                val groupPolicy = createGroupPolicyJson(membersNames)
                jarOutputStream.putNextEntry(ZipEntry("GroupPolicy.json"))
                jarOutputStream.write(groupPolicy)
                jarOutputStream.closeEntry()
            }
            outputStream.toByteArray()
        }
    }

    private fun getCheckSum(membersNames: Collection<String>): String {
        return testToolkit.httpClientFor(CpiUploadRPCOps::class.java).use { client ->
            val proxy = client.start().proxy
            val jar = createEmptyJarWithManifest(membersNames)
            val upload = HttpFileUpload(
                content = jar.inputStream(),
                contentType = "application/java-archive",
                extension = "cpb",
                fileName = "${testToolkit.uniqueName}.cpb",
                size = jar.size.toLong(),
            )
            val id = proxy.cpi(upload).id
            eventually {
                val status = proxy.status(id)
                assertThat(status.status).isEqualTo("OK")
                status.cpiFileChecksum
            }
        }
    }

    private fun String.clearX500Name(): String {
        return MemberX500Name.parse(this).toString()
    }

    private fun createVirtualNodes(membersNames: Collection<String>): Map<String, String> {
        val checksum = getCheckSum(membersNames)

        return testToolkit.httpClientFor(VirtualNodeRPCOps::class.java).use { client ->
            val proxy = client.start().proxy
            membersNames.associateWith {
                proxy.createVirtualNode(
                    VirtualNodeRequest(
                        x500Name = it,
                        cpiFileChecksum = checksum,
                        vaultDdlConnection = null,
                        vaultDmlConnection = null,
                        cryptoDdlConnection = null,
                        cryptoDmlConnection = null,
                    )
                ).holdingIdentity.shortHash
            }
        }
    }

    @Test
    fun `register members`() {
        val membersNames = (1..5).map {
            "C=GB, L=London, O=Member-${testToolkit.uniqueName}"
        }
        val holdingIds = createVirtualNodes(membersNames)

        testToolkit.httpClientFor(MemberRegistrationRpcOps::class.java).use { client ->
            val proxy = client.start().proxy
            holdingIds.values.forEach { id ->
                val registrationRequestProgress = proxy.startRegistration(
                    id,
                    MemberRegistrationRequest(
                        action = "requestJoin",
                        context = mapOf(
                            "corda.key.scheme" to "CORDA.ECDSA.SECP256R1"
                        )
                    )
                )
                assertThat(registrationRequestProgress.registrationStatus).isEqualTo("SUBMITTED")
            }
        }

        testToolkit.httpClientFor(MemberLookupRpcOps::class.java).use { client ->
            val proxy = client.start().proxy
            holdingIds.values.forEach { id ->
                val members = proxy.lookup(id).members

                assertThat(members)
                    .hasSize(holdingIds.size)
                    .allSatisfy {
                        assertThat(it.mgmContext["corda.status"]).isEqualTo("ACTIVE")
                        assertThat(it.memberContext["corda.groupId"]).isEqualTo(groupId)
                    }
                val names = members.map { it.memberContext["corda.name"] }
                assertThat(names)
                    .containsExactlyInAnyOrderElementsOf(
                        membersNames.map {
                            it.clearX500Name()
                        }
                    )
            }
        }
    }

    /*
    This test is disabled until CORE-6079 is ready.
    To run it locally use either:
    1. Minikube:
        Start the cluster by using https://gist.github.com/yift-r3/3461c5b116de1e197f2cc69d54d34f56
        and set the environment variables as describe at the end of the script.
    2. To connect to a running cluster:
        Connect to the cluster using https://gist.github.com/yift-r3/f975083b686c48193ca12077bd1dfa34
        and set the environment variables as describe at the end of the script.
    3. AWS:
        Start the cluster by using https://gist.github.com/yift-r3/ffa55f1171787d88b3b5370bda5823b8
        and set the environment variables as describe at the end of the script.
     */
    @Test
    @Disabled("This test is disabled until CORE-6079 is ready")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    fun `send P2P messages`() {
        // Create two identities
        val sender = HoldingIdentity(
            "C=GB, L=London, O=Member-${testToolkit.uniqueName}",
            groupId,
        )

        val receiver = HoldingIdentity(
            "C=GB, L=London, O=Member-${testToolkit.uniqueName}",
            groupId,
        )
        val holdingIds = createVirtualNodes(listOf(sender.x500Name, receiver.x500Name))

        // Register the identities
        testToolkit.httpClientFor(MemberRegistrationRpcOps::class.java).use { client ->
            val proxy = client.start().proxy
            holdingIds.values.forEach { id ->
                val registrationRequestProgress = proxy.startRegistration(
                    id,
                    MemberRegistrationRequest(
                        action = "requestJoin",
                        context = mapOf(
                            "corda.key.scheme" to "CORDA.ECDSA.SECP256R1"
                        )
                    )
                )
                assertThat(registrationRequestProgress.registrationStatus).isEqualTo("SUBMITTED")
            }
        }

        val traceId = "e2e-test-$groupId"
        val subSystem = "e2e-test"

        // Create authenticated messages
        val numberOfAuthenticatedMessages = 5
        val authenticatedMessagesIdToContent = (1..numberOfAuthenticatedMessages).associate {
            testToolkit.uniqueName to testToolkit.uniqueName
        }
        val authenticatedRecords = authenticatedMessagesIdToContent.map { (id, content) ->
            val messageHeader = AuthenticatedMessageHeader.newBuilder()
                .setDestination(receiver)
                .setSource(sender)
                .setTtl(null)
                .setMessageId(id)
                .setTraceId(traceId)
                .setSubsystem(subSystem)
                .build()
            val message = AuthenticatedMessage.newBuilder()
                .setHeader(messageHeader)
                .setPayload(ByteBuffer.wrap(content.toByteArray()))
                .build()
            Record(P2P_OUT_TOPIC, testToolkit.uniqueName, AppMessage(message))
        }

        // Create unauthenticated messages
        val numberOfUnauthenticatedMessages = 3
        val unauthenticatedMessagesContent = (1..numberOfUnauthenticatedMessages).map {
            testToolkit.uniqueName
        }
        val unauthenticatedRecords = unauthenticatedMessagesContent.map { content ->
            val messageHeader = UnauthenticatedMessageHeader.newBuilder()
                .setDestination(receiver)
                .setSource(sender)
                .setSubsystem(subSystem)
                .build()
            val message = UnauthenticatedMessage.newBuilder()
                .setHeader(messageHeader)
                .setPayload(ByteBuffer.wrap(content.toByteArray()))
                .build()
            Record(P2P_OUT_TOPIC, testToolkit.uniqueName, AppMessage(message))
        }

        // Accept messages
        val receivedAuthenticatedMessages = ConcurrentHashMap<String, String>()
        val receivedUnauthenticatedMessages = ConcurrentHashMap.newKeySet<String>()
        val countDown = CountDownLatch(numberOfUnauthenticatedMessages + numberOfAuthenticatedMessages)
        kafkaToolKit.acceptRecordsFromKafka<String, AppMessage>(P2P_IN_TOPIC) { record ->
            val message = record.value?.message
            if (message is AuthenticatedMessage) {
                if (message.header.destination.x500Name.clearX500Name() != receiver.x500Name.clearX500Name()) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.destination.groupId != groupId) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.source.x500Name.clearX500Name() != sender.x500Name.clearX500Name()) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.source.groupId != groupId) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.traceId != traceId) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.subsystem != subSystem) {
                    return@acceptRecordsFromKafka
                }
                receivedAuthenticatedMessages[message.header.messageId] = String(message.payload.array())
                countDown.countDown()
            } else if (message is UnauthenticatedMessage) {
                if (message.header.destination.x500Name.clearX500Name() != receiver.x500Name.clearX500Name()) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.destination.groupId != groupId) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.source.x500Name.clearX500Name() != sender.x500Name.clearX500Name()) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.source.groupId != groupId) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.subsystem != subSystem) {
                    return@acceptRecordsFromKafka
                }
                receivedUnauthenticatedMessages.add(String(message.payload.array()))
                countDown.countDown()
            }
        }.use {
            // Send messages
            kafkaToolKit.publishRecordsToKafka(unauthenticatedRecords + authenticatedRecords)
            countDown.await(5, TimeUnit.MINUTES)
        }

        assertThat(receivedAuthenticatedMessages).containsAllEntriesOf(authenticatedMessagesIdToContent)
        assertThat(receivedUnauthenticatedMessages).containsAll(unauthenticatedMessagesContent)
    }
}

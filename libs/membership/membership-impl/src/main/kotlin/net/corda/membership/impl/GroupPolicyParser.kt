package net.corda.membership.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.membership.GroupPolicy
import net.corda.membership.exceptions.BadGroupPolicyException
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.calculateHash
import net.corda.v5.membership.MemberInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey

@Component(service = [GroupPolicyParser::class])
class GroupPolicyParser @Activate constructor(
    @Reference(service = KeyEncodingService::class)
    val keyEncodingService: KeyEncodingService,
    @Reference(service = LayeredPropertyMapFactory::class)
    val layeredPropertyMapFactory: LayeredPropertyMapFactory
) {
    companion object {
        private val logger = contextLogger()
        const val EMPTY_GROUP_POLICY = "GroupPolicy file is empty."
        const val NULL_GROUP_POLICY = "GroupPolicy file is null."
        const val FAILED_PARSING = "GroupPolicy file is incorrectly formatted and parsing failed."
    }

    private val objectMapper = ObjectMapper()

    /**
     * Parses a GroupPolicy from [String] to [GroupPolicy].
     *
     * @throws [BadGroupPolicyException] if the input string is null, blank, or cannot be parsed.
     */
    @Suppress("ThrowsCount")
    fun parse(groupPolicyJson: String?): GroupPolicy {
        return GroupPolicyImpl(
            when {
                groupPolicyJson == null -> {
                    logger.error(NULL_GROUP_POLICY)
                    throw BadGroupPolicyException(NULL_GROUP_POLICY)
                }
                groupPolicyJson.isBlank() -> {
                    logger.error(EMPTY_GROUP_POLICY)
                    throw BadGroupPolicyException(EMPTY_GROUP_POLICY)
                }
                else -> {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        objectMapper.readValue(groupPolicyJson, Map::class.java) as Map<String, Any>
                    } catch (e: Exception) {
                        logger.error("$FAILED_PARSING Caused by: ${e.message}")
                        throw BadGroupPolicyException(FAILED_PARSING, e)
                    }
                }
            }
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun buildMgmMemberInfo(mgm: MGM, groupId: String): MemberInfo {
        val keys = mgm.keyList.map {
            keyEncodingService.decodePublicKey(it)
        }
        val now = UTCClock().instant().toString()
        return MemberInfoImpl(
            memberProvidedContext = layeredPropertyMapFactory.create<MemberContextImpl>(
                sortedMapOf(
                    MemberInfoExtension.PARTY_NAME to mgm.name,
                    MemberInfoExtension.PARTY_OWNING_KEY to mgm.sessionKey,
                    MemberInfoExtension.ECDH_KEY to mgm.ecdhKey,
                    MemberInfoExtension.GROUP_ID to groupId,
                    *convertKeys(mgm.keyList).toTypedArray(),
                    *generateKeyHashes(keys).toTypedArray(),
                    *convertEndpoints(mgm[MGM.ENDPOINTS] as List<Map<String, Any>>).toTypedArray(),
                    *convertCertificates(mgm.certificate).toTypedArray(),
                    MemberInfoExtension.SOFTWARE_VERSION to mgm.softwareVersion,
                    MemberInfoExtension.PLATFORM_VERSION to mgm.platformVersion,
                    MemberInfoExtension.SERIAL to mgm.serial
                )
            ),
            mgmProvidedContext = layeredPropertyMapFactory.create<MGMContextImpl>(
                sortedMapOf(
                    MemberInfoExtension.CREATED_TIME to now,
                    MemberInfoExtension.MODIFIED_TIME to now,
                    MemberInfoExtension.STATUS to MemberInfoExtension.MEMBER_STATUS_ACTIVE,
                    "mgm" to "true"
                )
            )
        )

    }

    /**
     * Mapping keys from JSON format to the keys expected in [MemberInfo].
     */
    private fun convertKeys(
        keys: List<String>
    ): List<Pair<String, String>> {
        require(keys.isNotEmpty()) { "List of MGM keys cannot be empty." }
        return keys.mapIndexed { index, identityKey ->
            String.format(
                MemberInfoExtension.IDENTITY_KEYS_KEY,
                index
            ) to identityKey
        }
    }

    /**
     * Generates key hashes from MGM keys.
     */
    private fun generateKeyHashes(
        keys: List<PublicKey>
    ): List<Pair<String, String>> {
        return keys.mapIndexed { index, identityKey ->
            val hash = identityKey.calculateHash()
            String.format(
                MemberInfoExtension.IDENTITY_KEY_HASHES_KEY,
                index
            ) to hash.toString()
        }
    }

    /**
     * Mapping certificates from JSON format to the certificates expected in [MemberInfo].
     */
    private fun convertCertificates(certificates: List<String>): List<Pair<String, String>> {
        require(certificates.isNotEmpty()) { "List of MGM certificates cannot be empty." }
        val result = mutableListOf<Pair<String, String>>()
        for (index in certificates.indices) {
            result.add(
                Pair(
                    String.format(MemberInfoExtension.CERTIFICATE_KEY, index),
                    certificates[index]
                )
            )
        }
        return result
    }

    /**
     * Mapping endpoints from JSON format to the endpoints expected in [MemberInfo].
     */
    private fun convertEndpoints(endpoints: List<Map<String, Any>>): List<Pair<String, String>> {
        require(endpoints.isNotEmpty()) { "List of MGM endpoints cannot be empty." }
        val result = mutableListOf<Pair<String, String>>()
        for (index in endpoints.indices) {
            result.add(
                Pair(
                    String.format(MemberInfoExtension.URL_KEY, index),
                    endpoints[index]["url"].toString()
                )
            )
            result.add(
                Pair(
                    String.format(MemberInfoExtension.PROTOCOL_VERSION, index),
                    endpoints[index]["protocolVersion"].toString()
                )
            )
        }
        return result
    }


    class MGM(private val mgmData: Map<String, Any>) : Map<String, Any> by mgmData {
        companion object {
            /** Key name for MGM in the network. */
            const val MGM_INFO = "mgmInfo"

            /** Key name for MGM's name. */
            const val NAME = "name"

            /** Key name for MGM session key. */
            const val SESSION_KEY = "sessionKey"

            /** Key name for MGM certificate. */
            const val CERTIFICATE = "certificate"

            /** Key name for MGM ECDH key. */
            const val ECDH_KEY = "ecdhKey"

            /** Key name for MGM keys. */
            const val KEYS = "keys"

            /** Key name for MGM endpoints. */
            const val ENDPOINTS = "endpoints"

            /** Key name for MGM platform version. */
            const val PLATFORM_VERSION = "platformVersion"

            /** Key name for MGM software version. */
            const val SOFTWARE_VERSION = "softwareVersion"

            /** Key name for MGM serial. */
            const val SERIAL = "serial"

            /** MGM info. */
            @JvmStatic
            @Suppress("UNCHECKED_CAST")
            val GroupPolicy.mgmInfo: MGM
                get() = if (containsKey(MGM_INFO)) {
                    get(MGM_INFO) as? MGM
                        ?: throw ClassCastException("Casting failed for MGM info from group policy JSON.")
                } else {
                    MGM(emptyMap())
                }
        }
        val name: String
            get() = getStringValue(NAME)!!

        val sessionKey: String
            get() = getStringValue(SESSION_KEY)!!

        val certificate: List<String>
            get() = getListValue(CERTIFICATE)

        val ecdhKey: String
            get() = getStringValue(ECDH_KEY)!!

        val keyList: List<String>
            get() = getListValue(KEYS)

        val platformVersion: String
            get() = getIntValueAsString(PLATFORM_VERSION)!!

        val softwareVersion: String
            get() = getStringValue(SOFTWARE_VERSION)!!

        val serial: String
            get() = getIntValueAsString(SERIAL)!!

        private fun getStringValue(key: String, default: String? = null): String? =
            mgmData[key] as String? ?: default

        @Suppress("UNCHECKED_CAST")
        private fun getListValue(key: String): List<String> =
            mgmData[key] as List<String>? ?: emptyList()

        private fun getIntValueAsString(key: String, default: String? = null): String? =
            (mgmData[key] as Int?)?.toString() ?: default
    }
}

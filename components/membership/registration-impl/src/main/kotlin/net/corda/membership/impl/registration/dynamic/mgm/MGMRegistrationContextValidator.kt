package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.configuration.read.ConfigurationGetService
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.ShortHashException
import net.corda.membership.impl.registration.dynamic.mgm.ContextUtils.sessionKeyRegex
import net.corda.membership.impl.registration.verifiers.OrderVerifier
import net.corda.membership.impl.registration.verifiers.P2pEndpointVerifier
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.membership.lib.toMap
import net.corda.schema.membership.MembershipSchema
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.versioning.Version
import org.slf4j.LoggerFactory
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateFactory
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.util.Date

internal class MGMRegistrationContextValidator(
    private val membershipSchemaValidatorFactory: MembershipSchemaValidatorFactory,
    private val orderVerifier: OrderVerifier = OrderVerifier(),
    private val p2pEndpointVerifier: P2pEndpointVerifier = P2pEndpointVerifier(orderVerifier),
    private val configurationGetService: ConfigurationGetService,
    private val clock: Clock
) {

    private companion object {
        const val errorMessageTemplate = "No %s was provided."

        val errorMessageMap = errorMessageTemplate.run {
            mapOf(
                ECDH_KEY_ID to format("ECDH key"),
                REGISTRATION_PROTOCOL to format("registration protocol"),
                SYNCHRONISATION_PROTOCOL to format("synchronisation protocol"),
                P2P_MODE to format("P2P mode"),
                SESSION_KEY_POLICY to format("session key policy"),
                PKI_SESSION to format("session PKI property"),
                PKI_TLS to format("TLS PKI property"),
            )
        }

        val SUPPORTED_REGISTRATION_PROTOCOLS = setOf(
            "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService"
        )
        val SUPPORTED_SYNC_PROTOCOLS = setOf(
            "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl"
        )
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val certificateFactory = CertificateFactory.getInstance("X.509")

    @Suppress("ThrowsCount")
    @Throws(MGMRegistrationContextValidationException::class)
    fun validate(context: Map<String, String>) {
        try {
            validateContextSchema(context)
            validateContext(context)
        } catch (ex: MembershipSchemaValidationException) {
            throw MGMRegistrationContextValidationException(
                "Onboarding MGM failed. ${ex.message}",
                ex
            )
        } catch (ex: IllegalArgumentException) {
            throw MGMRegistrationContextValidationException(
                "Onboarding MGM failed. ${ex.message ?: "Reason unknown."}",
                ex
            )
        } catch (ex: Exception) {
            throw MGMRegistrationContextValidationException(
                "Onboarding MGM failed. Unexpected error occurred during context validation. ${ex.message}",
                ex
            )
        }
    }

    /**
     * Validates only endpoint changes are submitted for MGM re-registration.
     */
    @Throws(MGMRegistrationContextValidationException::class)
    fun validateMemberContext(
        newContext: SelfSignedMemberInfo,
        lastMemberInfo: SelfSignedMemberInfo
    ) {
        val newMemberContext = newContext.memberProvidedContext.toMap()
        val lastMemberContext = lastMemberInfo.memberProvidedContext.toMap()
        val diff = ((newMemberContext.entries - lastMemberContext.entries) + (lastMemberContext.entries - newMemberContext.entries))
            .filterNot {
                it.key.startsWith(MemberInfoExtension.ENDPOINTS) ||
                    it.key.startsWith(MemberInfoExtension.PLATFORM_VERSION) ||
                    it.key.startsWith(MemberInfoExtension.SOFTWARE_VERSION)
            }
        if (diff.isNotEmpty()) {
            throw MGMRegistrationContextValidationException(
                "Fields ${diff.map { it.key }.toSet()} cannot be added, removed or updated during MGM re-registration.",
                null
            )
        }
    }

    /**
     * Validates there were no group policy related updates submitted for MGM re-registration.
     */
    @Throws(MGMRegistrationContextValidationException::class)
    fun validateGroupPolicy(registrationContext: Map<String, String>, lastGroupPolicy: LayeredPropertyMap) {
        val groupPolicy = registrationContext.filterKeys { it.startsWith(GROUP_POLICY_PREFIX) }
            .mapKeys { it.key.replace("$GROUP_POLICY_PREFIX.", "") }
        val diff = ((groupPolicy.entries - lastGroupPolicy.entries) + (lastGroupPolicy.entries - groupPolicy.entries))
        if (diff.isNotEmpty()) {
            throw MGMRegistrationContextValidationException(
                "Fields ${diff.map { it.key }.toSet()} cannot be added, removed or updated during MGM re-registration.",
                null
            )
        }
    }

    @Throws(MembershipSchemaValidationException::class)
    private fun validateContextSchema(context: Map<String, String>) {
        membershipSchemaValidatorFactory
            .createValidator()
            .validateRegistrationContext(
                MembershipSchema.RegistrationContextSchema.Mgm,
                Version(1, 0),
                context
            )
    }

    @Throws(IllegalArgumentException::class)
    @Suppress("ThrowsCount")
    private fun validateContext(context: Map<String, String>) {
        for (key in errorMessageMap.keys) {
            context[key] ?: throw IllegalArgumentException(errorMessageMap[key])
        }
        validateProtocols(context)
        validateKeys(context)
        p2pEndpointVerifier.verifyContext(context)
        if (context[PKI_SESSION] != NO_PKI.toString()) {
            context.keys.filter { TRUSTSTORE_SESSION.format("[0-9]+").toRegex().matches(it) }.apply {
                require(isNotEmpty()) { "No session trust store was provided." }
                require(orderVerifier.isOrdered(this, 4)) { "Provided session trust stores are incorrectly numbered." }
                this.map { key ->
                    validateTrustrootCert(context[key]!!, key)
                }
            }
        }
        context.keys.filter { TRUSTSTORE_TLS.format("[0-9]+").toRegex().matches(it) }.apply {
            require(isNotEmpty()) { "No TLS trust store was provided." }
            require(orderVerifier.isOrdered(this, 4)) { "Provided TLS trust stores are incorrectly numbered." }
            this.map { key ->
                validateTrustrootCert(context[key]!!, key)
            }
        }

        context.keys.filter { SESSION_KEY_IDS.format("[0-9]+").toRegex().matches(it) }.apply {
            require(isNotEmpty()) { "No session key was provided." }
            require(orderVerifier.isOrdered(this, 3)) { "Provided session keys are incorrectly numbered." }
        }
        val contextRegistrationTlsType = context[TLS_TYPE]?.let { tlsType ->
            TlsType.fromString(tlsType) ?: throw IllegalArgumentException("Invalid TLS type: $tlsType")
        } ?: TlsType.ONE_WAY
        val clusterTlsType = TlsType.getClusterType(configurationGetService::getSmartConfig)
        if (contextRegistrationTlsType != clusterTlsType) {
            throw IllegalArgumentException(
                "A cluster configured with TLS type of $clusterTlsType can not register " +
                    "an MGM with TLS type $contextRegistrationTlsType"
            )
        }
    }

    private fun validateProtocols(context: Map<String, String>) {
        if (context[REGISTRATION_PROTOCOL] !in SUPPORTED_REGISTRATION_PROTOCOLS) {
            throw MGMRegistrationContextValidationException(
                "Invalid value for key $REGISTRATION_PROTOCOL in registration context. " +
                    "It should be one of the following values: $SUPPORTED_REGISTRATION_PROTOCOLS.",
                null
            )
        }
        if (context[SYNCHRONISATION_PROTOCOL] !in SUPPORTED_SYNC_PROTOCOLS) {
            throw MGMRegistrationContextValidationException(
                "Invalid value for key $SYNCHRONISATION_PROTOCOL in registration context. " +
                    "It should be one of the following values: $SUPPORTED_SYNC_PROTOCOLS.",
                null
            )
        }
    }

    private fun validateKeys(context: Map<String, String>) {
        val ecdhKeyId = context[ECDH_KEY_ID]
        require(ecdhKeyId != null) { "No ECDH key ID was provided under $ECDH_KEY_ID." }
        validateKey(ECDH_KEY_ID, ecdhKeyId)

        context.filterKeys { key ->
            sessionKeyRegex.matches(key)
        }.forEach {
            validateKey(it.key, it.value)
        }
    }

    private fun validateKey(contextKey: String, keyId: String) {
        try {
            ShortHash.parse(keyId)
        } catch (e: ShortHashException) {
            throw MGMRegistrationContextValidationException("Invalid value for key ID $contextKey. ${e.message}", e)
        }
    }

    @Suppress("ThrowsCount")
    private fun validateTrustrootCert(pemCert: String, key: String) {
        val certificate = try {
            certificateFactory.generateCertificate(pemCert.byteInputStream())
        } catch (ex: CertificateException) {
            throw IllegalArgumentException(
                "Trust root certificate specified in registration context under key $key " +
                    "was not a valid PEM certificate."
            )
        }

        try {
            (certificate as X509Certificate).checkValidity(Date.from(clock.instant()))
        } catch (ex: Exception) {
            when (ex) {
                is CertificateExpiredException, is CertificateNotYetValidException -> {
                    throw IllegalArgumentException(
                        "Trust root certificate specified in registration context under key $key " +
                            "does not have a valid validity period."
                    )
                }
                else -> throw ex
            }
        }
    }
}

internal class MGMRegistrationContextValidationException(
    val reason: String,
    e: Throwable?
) : CordaRuntimeException(reason, e)

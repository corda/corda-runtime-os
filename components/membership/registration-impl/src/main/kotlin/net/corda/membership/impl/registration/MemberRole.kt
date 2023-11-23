package net.corda.membership.impl.registration

import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_PEM
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_BACKCHAIN_REQUIRED
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL_VERSIONS
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.v5.base.types.MemberX500Name

internal sealed class MemberRole {
    companion object {
        fun extractRolesFromContext(context: Map<String, String>): Collection<MemberRole> {
            val roleNames = context.entries.filter {
                it.key.startsWith(ROLES_PREFIX)
            }.map {
                it.value
            }.toSet()
            val roles: Collection<MemberRole> = roleNames.map { roleName ->
                when (roleName) {
                    NOTARY_ROLE -> {
                        readNotary(context)
                    }

                    else -> {
                        throw IllegalArgumentException("Invalid role: $roleName")
                    }
                }
            }
            if (!roles.any { it is Notary }) {
                val notaryKeys = context.keys.filter {
                    it.startsWith("corda.notary")
                }
                if (notaryKeys.isNotEmpty()) {
                    throw IllegalArgumentException("The keys $notaryKeys are only valid with notary role.")
                }
            }
            return roles
        }

        fun Collection<MemberRole>.toMemberInfo(
            notariesKeysFactory: () -> List<KeyDetails>,
        ): Collection<Pair<String, String>> {
            return this.flatMapIndexed { index, role ->
                role.toMemberInfo(notariesKeysFactory, index)
            }
        }

        @Suppress("ThrowsCount")
        private fun readNotary(context: Map<String, String>): Notary {
            val serviceName = context[NOTARY_SERVICE_NAME]
            if(serviceName.isNullOrEmpty()) throw IllegalArgumentException("Notary must have a non-empty service name.")
            val protocol = context[NOTARY_SERVICE_PROTOCOL]
            if (protocol == null) {
                throw IllegalArgumentException("No value provided for $NOTARY_SERVICE_PROTOCOL, which is required for a notary.")
            }
            if (protocol.isBlank()) {
                throw IllegalArgumentException("Value provided for $NOTARY_SERVICE_PROTOCOL was a blank string." )
            }
            val protocolVersions = NOTARY_SERVICE_PROTOCOL_VERSIONS.format("([0-9]+)").toRegex().let { regex ->
                context.filter { it.key.matches(regex) }.mapTo(mutableSetOf()) { it.value.toInt() }
            }
            return Notary(
                serviceName = MemberX500Name.parse(serviceName),
                protocol = protocol,
                protocolVersions = protocolVersions,
                backchainRequired =  context[NOTARY_SERVICE_BACKCHAIN_REQUIRED].toBoolean()
            )
        }
    }

    protected abstract fun toMemberInfo(
        notariesKeysFactory: () -> List<KeyDetails>,
        index: Int,
    ): Collection<Pair<String, String>>

    data class Notary(
        val serviceName: MemberX500Name,
        val protocol: String,
        val protocolVersions: Collection<Int>,
        val backchainRequired: Boolean
    ) : MemberRole() {
        override fun toMemberInfo(
            notariesKeysFactory: () -> List<KeyDetails>,
            index: Int,
        ): Collection<Pair<String, String>> {
            val keys = notariesKeysFactory().flatMapIndexed { keyIndex, key ->
                listOf(
                    String.format(NOTARY_KEY_PEM, keyIndex) to key.pem,
                    String.format(NOTARY_KEY_HASH, keyIndex) to key.hash.toString(),
                    String.format(NOTARY_KEY_SPEC, keyIndex) to key.spec.signatureName,
                )
            }
            val versions = protocolVersions.flatMapIndexed { versionIndex, version ->
                listOf(
                    String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, versionIndex) to version.toString()
                )
            }
            return keys + versions + listOf(
                "$ROLES_PREFIX.$index" to NOTARY_ROLE,
                NOTARY_SERVICE_NAME to serviceName.toString(),
                NOTARY_SERVICE_PROTOCOL to protocol,
                NOTARY_SERVICE_BACKCHAIN_REQUIRED to backchainRequired.toString()
            )
        }
    }
}

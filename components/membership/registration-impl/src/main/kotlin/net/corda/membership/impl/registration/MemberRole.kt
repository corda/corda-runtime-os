package net.corda.membership.impl.registration

import net.corda.membership.lib.MemberInfoExtension.Companion.INTEROP_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.INTEROP_SERVICE_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_PEM
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PLUGIN
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

                    INTEROP_ROLE -> {
                        readInterop(context)
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

        private fun readNotary(context: Map<String, String>): Notary {
            val serviceName = context[NOTARY_SERVICE_NAME]
            if(serviceName.isNullOrEmpty()) throw IllegalArgumentException("Notary must have a non-empty service name.")
            val plugin = context[NOTARY_SERVICE_PLUGIN]
            return Notary(
                serviceName = MemberX500Name.parse(serviceName),
                plugin = plugin,
            )
        }

        private fun readInterop(context: Map<String, String>): Interop {
            val serviceName =
                context[INTEROP_SERVICE_NAME] ?: throw IllegalArgumentException("Interop must have a service name")
            return Interop(
                serviceName = MemberX500Name.parse(serviceName)
            )
        }
    }

    protected abstract fun toMemberInfo(
        notariesKeysFactory: () -> List<KeyDetails>,
        index: Int,
    ): Collection<Pair<String, String>>

    data class Notary(
        val serviceName: MemberX500Name,
        val plugin: String?,
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
            return keys + listOf(
                "$ROLES_PREFIX.$index" to NOTARY_ROLE,
                NOTARY_SERVICE_NAME to serviceName.toString(),
            ) + if (plugin == null) {
                emptyList()
            } else {
                listOf(
                    NOTARY_SERVICE_PLUGIN to plugin,
                )
            }
        }
    }
    data class Interop(
        val serviceName: MemberX500Name
    ) : MemberRole() {
        override fun toMemberInfo(
            notariesKeysFactory: () -> List<KeyDetails>,
            index: Int,
        ): Collection<Pair<String, String>> {
            return listOf(
                "$ROLES_PREFIX.$index" to INTEROP_ROLE,
                INTEROP_SERVICE_NAME to serviceName.toString(),
            )
        }
    }
}

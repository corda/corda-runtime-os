package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.membership.lib.MemberInfoExtension.Companion.ECDH_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEY

internal const val GROUP_POLICY_PREFIX = "corda.group"
internal const val GROUP_POLICY_PREFIX_WITH_DOT = "$GROUP_POLICY_PREFIX."
internal const val REGISTRATION_PROTOCOL = "${GROUP_POLICY_PREFIX}.protocol.registration"
internal const val SYNCHRONISATION_PROTOCOL = "${GROUP_POLICY_PREFIX}.protocol.synchronisation"
internal const val P2P_MODE = "${GROUP_POLICY_PREFIX}.protocol.p2p.mode"
internal const val SESSION_KEY_POLICY = "${GROUP_POLICY_PREFIX}.key.session.policy"
internal const val PKI_SESSION = "${GROUP_POLICY_PREFIX}.pki.session"
internal const val PKI_TLS = "${GROUP_POLICY_PREFIX}.pki.tls"
internal const val TRUSTSTORE_SESSION = "${GROUP_POLICY_PREFIX}.truststore.session.%s"
internal const val TRUSTSTORE_TLS = "${GROUP_POLICY_PREFIX}.truststore.tls.%s"
internal const val SESSION_KEY_ID = "$PARTY_SESSION_KEY.id"
internal const val ECDH_KEY_ID = "$ECDH_KEY.id"
internal const val TLS_TYPE = "$GROUP_POLICY_PREFIX.tls.type"
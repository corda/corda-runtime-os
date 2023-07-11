@file:JvmName("Constants")
package net.corda.testing.driver.sandbox

import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME

const val CORDA_GROUP_PID = "net.corda.testing.driver.sandbox.Group"
const val CORDA_GROUP_PARAMETERS = "net.corda.testing.driver.sandbox.group.parameters"
const val CORDA_MEMBERSHIP_PID = "net.corda.testing.driver.sandbox.Membership"
const val CORDA_MEMBER_COUNT = "net.corda.testing.driver.sandbox.member.count"
const val CORDA_MEMBER_X500_NAME = "net.corda.testing.driver.sandbox.member.X500"
const val CORDA_MEMBER_PUBLIC_KEY = "net.corda.testing.driver.sandbox.member.PublicKey"
const val CORDA_MEMBER_PRIVATE_KEY = "net.corda.testing.driver.sandbox.member.PrivateKey"
const val CORDA_LOCAL_IDENTITY_PID = "net.corda.testing.driver.sandbox.LocalIdentity"
const val CORDA_TENANT_COUNT = "net.corda.testing.driver.sandbox.tenant.count"
const val CORDA_TENANT = "net.corda.testing.driver.sandbox.tenant"
const val CORDA_TENANT_MEMBER = "net.corda.testing.driver.sandbox.tenant.member"
const val CORDA_LOCAL_TENANCY_PID = "net.corda.testing.driver.sandbox.LocalTenancy"

const val FIRST_SESSION_KEY = "$SESSION_KEYS.0"
const val DEFAULT_KEY_SCHEME = ECDSA_SECP256R1_CODE_NAME
const val WRAPPING_KEY_ALIAS = "master"

const val WAIT_MILLIS = 100L

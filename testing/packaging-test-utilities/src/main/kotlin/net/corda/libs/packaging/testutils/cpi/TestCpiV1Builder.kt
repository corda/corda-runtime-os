package net.corda.libs.packaging.testutils.cpi

import net.corda.libs.packaging.testutils.TestUtils
import net.corda.libs.packaging.testutils.TestUtils.addFile
import net.corda.libs.packaging.testutils.TestUtils.signedBy
import net.corda.libs.packaging.testutils.cpb.TestCpbV1Builder
import net.corda.test.util.InMemoryZipFile

class TestCpiV1Builder {
    companion object {
        val POLICY_FILE = "META-INF/GroupPolicy.json"
    }
    var policy = "{\"groupId\":\"test\"}"
        private set
    var cpb = TestCpbV1Builder()
        private set
    var signers: Array<out TestUtils.Signer> = emptyArray()
        private set

    fun policy(policy: String) = apply { this.policy = policy }
    fun cpb(cpb: TestCpbV1Builder) = apply { this.cpb = cpb }
    fun signers(vararg signers: TestUtils.Signer) = apply { this.signers = signers }

    fun build(): InMemoryZipFile {
        if (cpb.signers.isEmpty()) cpb.signers(signers = signers)
        return cpb.build().apply {
            addFile(POLICY_FILE, policy)
        }.signedBy(signers = signers)
    }
}
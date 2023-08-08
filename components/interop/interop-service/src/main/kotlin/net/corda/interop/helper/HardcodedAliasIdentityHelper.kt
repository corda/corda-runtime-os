package net.corda.interop.helper

import net.corda.v5.interop.InterOpIdentityInfo

class HardcodedAliasIdentityHelper {
    companion object {
        fun getAliasIdentityData(): List<InterOpIdentityInfo> {
            return listOf(
                object : InterOpIdentityInfo{
                    override fun applicationName() = "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08"
                    override fun getX500Name() = "C=GB, L=London, O=Bob2 Alias"
                    override fun getFacadeIds(): MutableList<String>
                        = mutableListOf("org.corda.interop/platform/tokens/v1.0")
                },
                InteropIdentityInfoImpl(
                    "Bob",
                    "Gold Trading",
                    listOf("org.corda.interop/platform/hello-interop/v1.0", "org.corda.interop/platform/tokens/v1.0")
                ),
                InteropIdentityInfoImpl(
                    "Bob",
                    "Silver Trading",
                    listOf("org.corda.interop/platform/hello-interop/v1.0", "org.corda.interop/platform/tokens/v1.0")
                ),
                InteropIdentityInfoImpl(
                    "Bob",
                    "Bronze Trading",
                    listOf("org.corda.interop/platform/hello-interop/v1.0", "org.corda.interop/platform/tokens/v1.0")
                ),
                InteropIdentityInfoImpl(
                    "Bob",
                    "UKBank",
                    listOf("org.corda.interop/platform/hello-interop/v1.0")
                ),
                InteropIdentityInfoImpl(
                    "Bob",
                    "EU Bank",
                    listOf("org.corda.interop/platform/hello-interop/v1.0")
                )
            )

        }
    }
}
data class InteropIdentityInfoImpl(
    private val member: String,
    private val hostNetwork: String,
    private val facadeIds: List<String>
) : InterOpIdentityInfo {
    override fun getX500Name(): String {
        return "O=$member Alias,CN=$hostNetwork, L=London, C=GB"
    }

    override fun applicationName(): String {
        return hostNetwork
    }

    override fun getFacadeIds(): List<String> {
        return facadeIds
    }
}

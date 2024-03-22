package net.corda.sdk.rest

import net.corda.membership.rest.v1.KeyRestResource
import net.corda.membership.rest.v1.MGMRestResource
import net.corda.rest.client.auth.credentials.BasicAuthCredentials
import net.corda.sdk.rest.RestClientUtils.createRestClientForTarget
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RestClientTargetInfoTest {

    @Test
    fun configureTargetOnce() {
        restClientTargetInfo = RestClientTargetInfo(
            targetUrl = "https://localhost:1234",
            username = "adam",
            password = "password",
        )

        val rc1 = createRestClientForTarget(MGMRestResource::class)
        assertThat(rc1.baseAddress()).startsWith("https://localhost:1234")
        val creds1 = rc1.clientConfig().authenticationConfig.getCredentialsProvider().getCredentials() as BasicAuthCredentials
        assertThat(creds1.username).isEqualTo("adam")
        assertThat(creds1.password).isEqualTo("password")

        val rc2 = createRestClientForTarget(KeyRestResource::class)
        assertThat(rc2.baseAddress()).startsWith("https://localhost:1234")
        val creds2 = rc2.clientConfig().authenticationConfig.getCredentialsProvider().getCredentials() as BasicAuthCredentials
        assertThat(creds2.username).isEqualTo("adam")
        assertThat(creds2.password).isEqualTo("password")

        rc1.ops
    }

    @Test
    fun targetInfoCanBeUpdated() {
        // Set initial value
        restClientTargetInfo = RestClientTargetInfo(
            targetUrl = "https://localhost:1234",
            username = "adam",
            password = "password",
        )

        // Create an RC which uses initial value
        val rc1 = createRestClientForTarget(MGMRestResource::class)
        assertThat(rc1.baseAddress()).startsWith("https://localhost:1234")

        // Change value
        restClientTargetInfo.targetUrl = "https://localhost:9999"

        // New RC picks up the new value
        val rc2 = createRestClientForTarget(KeyRestResource::class)
        assertThat(rc2.baseAddress()).startsWith("https://localhost:9999")

        // Pre-existing RC still retains old value
        assertThat(rc1.baseAddress()).startsWith("https://localhost:1234")
    }
}

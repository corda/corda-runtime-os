package net.corda.restclient

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.jackson.*
import net.corda.restclient.apis.CPIApi
import net.corda.restclient.apis.CertificateApi
import net.corda.restclient.apis.ConfigurationApi
import net.corda.restclient.apis.FlowInfoApi
import net.corda.restclient.apis.FlowManagementApi
import net.corda.restclient.apis.HSMApi
import net.corda.restclient.apis.HelloRestApi
import net.corda.restclient.apis.KeyManagementApi
import net.corda.restclient.apis.KeyRotationApi
import net.corda.restclient.apis.MGMAdminApi
import net.corda.restclient.apis.MGMApi
import net.corda.restclient.apis.MemberLookupApi
import net.corda.restclient.apis.MemberRegistrationApi
import net.corda.restclient.apis.NetworkApi
import net.corda.restclient.apis.RBACPermissionApi
import net.corda.restclient.apis.RBACRoleApi
import net.corda.restclient.apis.RBACUserApi
import net.corda.restclient.apis.VirtualNodeApi
import net.corda.restclient.apis.VirtualNodeMaintenanceApi
import net.corda.restclient.infrastructure.ApiClient

class CordaRestClient private constructor(
    private val baseUrl: String,
    private val httpClientEngine: HttpClientEngine,
    private val httpClientConfig: (HttpClientConfig<*>.() -> Unit)
) {

    private val certificatesApi by lazy { CertificateApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val configurationApi by lazy { ConfigurationApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val cpiApi by lazy { CPIApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val flowInfoApi by lazy { FlowInfoApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val flowManagementApi by lazy { FlowManagementApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val helloRestApi by lazy { HelloRestApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val hsmApi by lazy { HSMApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val keyManagementApi by lazy { KeyManagementApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val keyRotationApi by lazy { KeyRotationApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val memberLookupApi by lazy { MemberLookupApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val memberRegistrationApi by lazy { MemberRegistrationApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val mgmAdminApi by lazy { MGMAdminApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val mgmApi by lazy { MGMApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val networkApi by lazy { NetworkApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val rbacPermissionApi by lazy { RBACPermissionApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val rbacRoleApi by lazy { RBACRoleApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val rbacUserApi by lazy { RBACUserApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val virtualNodeApi by lazy { VirtualNodeApi(baseUrl, httpClientEngine, httpClientConfig) }
    private val virtualNodeMaintenanceApi by lazy {
        VirtualNodeMaintenanceApi(
            baseUrl,
            httpClientEngine,
            httpClientConfig
        )
    }


    fun certificatesClient(): CertificateApi = certificatesApi


    fun configurationClient(): ConfigurationApi = configurationApi

    fun cpiClient(): CPIApi = cpiApi

    fun flowInfoClient(): FlowInfoApi = flowInfoApi

    fun flowManagementClient(): FlowManagementApi = flowManagementApi

    fun helloRestClient(): HelloRestApi = helloRestApi

    fun hsmClient(): HSMApi = hsmApi

    fun keyManagementClient(): KeyManagementApi = keyManagementApi

    fun keyRotationClient(): KeyRotationApi = keyRotationApi

    fun memberLookupClient(): MemberLookupApi = memberLookupApi

    fun memberRegistrationClient(): MemberRegistrationApi = memberRegistrationApi

    fun mgmAdminClient(): MGMAdminApi = mgmAdminApi

    fun mgmClient(): MGMApi = mgmApi

    fun networkClient(): NetworkApi = networkApi

    fun rbacPermissionClient(): RBACPermissionApi = rbacPermissionApi

    fun rbacRoleClient(): RBACRoleApi = rbacRoleApi

    fun rbacUserClient(): RBACUserApi = rbacUserApi

    fun virtualNodeClient(): VirtualNodeApi = virtualNodeApi

    fun virtualNodeMaintenanceClient(): VirtualNodeMaintenanceApi = virtualNodeMaintenanceApi

    companion object {

        fun createHttpClient(
            baseUrl: String = "https://localhost:8888/${ApiClient.BASE_URL}",
            username: String = "admin",
            password: String = "admin"
        ): CordaRestClient {

            val httpClientEngine = CIO.create()
            val httpClientConfig: (HttpClientConfig<*>.() -> Unit) = {
                install(ContentNegotiation) {
                    jackson()
                }
                install(Auth) {
                    basic {
                        credentials {
                            BasicAuthCredentials(username, password)
                        }
                    }
                }
                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.BODY
                }
            }

            return CordaRestClient(baseUrl, httpClientEngine, httpClientConfig)
        }
    }
}

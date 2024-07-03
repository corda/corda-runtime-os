package net.corda.restclient

import net.corda.restclient.generated.apis.CPIApi
import net.corda.restclient.generated.apis.CertificateApi
import net.corda.restclient.generated.apis.ConfigurationApi
import net.corda.restclient.generated.apis.FlowInfoApi
import net.corda.restclient.generated.apis.FlowManagementApi
import net.corda.restclient.generated.apis.HSMApi
import net.corda.restclient.generated.apis.HelloRestApi
import net.corda.restclient.generated.apis.KeyManagementApi
import net.corda.restclient.generated.apis.KeyRotationApi
import net.corda.restclient.generated.apis.MGMAdminApi
import net.corda.restclient.generated.apis.MGMApi
import net.corda.restclient.generated.apis.MemberLookupApi
import net.corda.restclient.generated.apis.MemberRegistrationApi
import net.corda.restclient.generated.apis.NetworkApi
import net.corda.restclient.generated.apis.RBACPermissionApi
import net.corda.restclient.generated.apis.RBACRoleApi
import net.corda.restclient.generated.apis.RBACUserApi
import net.corda.restclient.generated.apis.VirtualNodeApi
import net.corda.restclient.generated.apis.VirtualNodeMaintenanceApi
import net.corda.restclient.generated.infrastructure.ApiClient
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


@Suppress("LongParameterList")
class CordaRestClient(
    val certificatesClient: CertificateApi,
    val configurationClient: ConfigurationApi,
    val cpiClient: CPIApi,
    val flowInfoClient: FlowInfoApi,
    val flowManagementClient: FlowManagementApi,
    val helloRestClient: HelloRestApi,
    val hsmClient: HSMApi,
    val keyManagementClient: KeyManagementApi,
    val keyRotationClient: KeyRotationApi,
    val memberLookupClient: MemberLookupApi,
    val memberRegistrationClient: MemberRegistrationApi,
    val mgmAdminClient: MGMAdminApi,
    val mgmClient: MGMApi,
    val networkClient: NetworkApi,
    val rbacPermissionClient: RBACPermissionApi,
    val rbacRoleClient: RBACRoleApi,
    val rbacUserClient: RBACUserApi,
    val virtualNodeClient: VirtualNodeApi,
    val virtualNodeMaintenanceClient: VirtualNodeMaintenanceApi,
) {

    companion object {
        private val defaultBasePath = HelloRestApi.defaultBasePath

        /**
         * Create an instance of CordaRestClient with the given baseUrl, username and password.
         * Please note, if you use this multiple times with different credentials, you will be overwriting the previous credentials.
         * e.g.
         * val adminClient = createHttpClient(baseUrl, "admin", adminPassword)
         * val userClient = createHttpClient(baseUrl, "user", userPassword)
         * The `adminClient` will have the credentials of the `userClient` after the second call.
         *
         * @param baseUrl The base URL of the Corda node.
         * @param username The username to authenticate with.
         * @param password The password to authenticate with.
         * @param insecure Whether to allow insecure connections. Default is false.
         * @param certificatesClient The CertificateApi instance to use. If null, a new instance will be created.
         * @param configurationClient The ConfigurationApi instance to use. If null, a new instance will be created.
         * @param cpiClient The CPIApi instance to use. If null, a new instance will be created.
         * @param flowInfoClient The FlowInfoApi instance to use. If null, a new instance will be created.
         * @param flowManagementClient The FlowManagementApi instance to use. If null, a new instance will be created.
         * @param helloRestClient The HelloRestApi instance to use. If null, a new instance will be created.
         * @param hsmClient The HSMApi instance to use. If null, a new instance will be created.
         * @param keyManagementClient The KeyManagementApi instance to use. If null, a new instance will be created.
         * @param keyRotationClient The KeyRotationApi instance to use. If null, a new instance will be created.
         * @param memberLookupClient The MemberLookupApi instance to use. If null, a new instance will be created.
         * @param memberRegistrationClient The MemberRegistrationApi instance to use. If null, a new instance will be created.
         * @param mgmAdminClient The MGMAdminApi instance to use. If null, a new instance will be created.
         * @param mgmClient The MGMApi instance to use. If null, a new instance will be created.
         * @param networkClient The NetworkApi instance to use. If null, a new instance will be created.
         * @param rbacPermissionClient The RBACPermissionApi instance to use. If null, a new instance will be created.
         * @param rbacRoleClient The RBACRoleApi instance to use. If null, a new instance will be created.
         * @param rbacUserClient The RBACUserApi instance to use. If null, a new instance will be created.
         * @param virtualNodeClient The VirtualNodeApi instance to use. If null, a new instance will be created.
         * @param virtualNodeMaintenanceClient The VirtualNodeMaintenanceApi instance to use. If null, a new instance will be created.
         * @return CordaRestClient instance.
         */
        @Suppress("LongParameterList")
        fun createHttpClient(
            baseUrl: URI = URI.create("https://localhost:8888"),
            username: String = "admin",
            password: String = "admin",
            insecure: Boolean = false,
            certificatesClient: CertificateApi? = null,
            configurationClient: ConfigurationApi? = null,
            cpiClient: CPIApi? = null,
            flowInfoClient: FlowInfoApi? = null,
            flowManagementClient: FlowManagementApi? = null,
            helloRestClient: HelloRestApi? = null,
            hsmClient: HSMApi? = null,
            keyManagementClient: KeyManagementApi? = null,
            keyRotationClient: KeyRotationApi? = null,
            memberLookupClient: MemberLookupApi? = null,
            memberRegistrationClient: MemberRegistrationApi? = null,
            mgmAdminClient: MGMAdminApi? = null,
            mgmClient: MGMApi? = null,
            networkClient: NetworkApi? = null,
            rbacPermissionClient: RBACPermissionApi? = null,
            rbacRoleClient: RBACRoleApi? = null,
            rbacUserClient: RBACUserApi? = null,
            virtualNodeClient: VirtualNodeApi? = null,
            virtualNodeMaintenanceClient: VirtualNodeMaintenanceApi? = null
        ): CordaRestClient {

            val urlWithDefaultBasePath = baseUrl.toString() + defaultBasePath

            val builder = ApiClient.apply {
                this.username = username
                this.password = password
            }.builder

            // Disable SSL verification if insecure is true
            if (insecure) {
                val sslContext = SSLContext.getInstance("TLSv1.3")
                sslContext.init(null, trustAllCerts, SecureRandom())
                builder
                    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
            }

            val client = builder.build()
            return CordaRestClient(
                certificatesClient = certificatesClient ?: CertificateApi(urlWithDefaultBasePath, client),
                configurationClient = configurationClient ?: ConfigurationApi(urlWithDefaultBasePath, client),
                cpiClient = cpiClient ?: CPIApi(urlWithDefaultBasePath, client),
                flowInfoClient = flowInfoClient ?: FlowInfoApi(urlWithDefaultBasePath, client),
                flowManagementClient = flowManagementClient ?: FlowManagementApi(urlWithDefaultBasePath, client),
                helloRestClient = helloRestClient ?: HelloRestApi(urlWithDefaultBasePath, client),
                hsmClient = hsmClient ?: HSMApi(urlWithDefaultBasePath, client),
                keyManagementClient = keyManagementClient ?: KeyManagementApi(urlWithDefaultBasePath, client),
                keyRotationClient = keyRotationClient ?: KeyRotationApi(urlWithDefaultBasePath, client),
                memberLookupClient = memberLookupClient ?: MemberLookupApi(urlWithDefaultBasePath, client),
                memberRegistrationClient = memberRegistrationClient ?: MemberRegistrationApi(urlWithDefaultBasePath, client),
                mgmAdminClient = mgmAdminClient ?: MGMAdminApi(urlWithDefaultBasePath, client),
                mgmClient = mgmClient ?: MGMApi(urlWithDefaultBasePath, client),
                networkClient = networkClient ?: NetworkApi(urlWithDefaultBasePath, client),
                rbacPermissionClient = rbacPermissionClient ?: RBACPermissionApi(urlWithDefaultBasePath, client),
                rbacRoleClient = rbacRoleClient ?: RBACRoleApi(urlWithDefaultBasePath, client),
                rbacUserClient = rbacUserClient ?: RBACUserApi(urlWithDefaultBasePath, client),
                virtualNodeClient = virtualNodeClient ?: VirtualNodeApi(urlWithDefaultBasePath, client),
                virtualNodeMaintenanceClient = virtualNodeMaintenanceClient ?: VirtualNodeMaintenanceApi(urlWithDefaultBasePath, client)
            )
        }

        // Create a trust manager that does not validate certificate chains
        private val trustAllCerts: Array<TrustManager> = arrayOf(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                    // Do nothing
                }
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    // Do nothing
                }
                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return arrayOf()
                }
            }
        )
    }
}

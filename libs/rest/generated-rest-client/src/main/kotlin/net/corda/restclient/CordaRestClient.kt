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
import okhttp3.OkHttpClient
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


@Suppress("LongParameterList")
class CordaRestClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient,
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
        private const val API_VERSION = "/api/v5_3"

        /**
         * Create an instance of CordaRestClient with the given baseUrl, username and password.
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
            baseUrl: String = "https://localhost:8888",
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

            val urlWithCordaApiVersion = baseUrl + API_VERSION

            val builder = ApiClient.apply {
                this.username = username
                this.password = password
            }.builder

            // Disable SSL verification if insecure is true
            if (insecure) {
                val (trustAllCerts: Array<TrustManager>, sslContext) = prepareInsecureSettings()
                builder
                    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
            }

            val client = builder.build()
            return CordaRestClient(
                baseUrl = urlWithCordaApiVersion,
                httpClient = client,
                certificatesClient = certificatesClient ?: CertificateApi(urlWithCordaApiVersion, client),
                configurationClient = configurationClient ?: ConfigurationApi(urlWithCordaApiVersion, client),
                cpiClient = cpiClient ?: CPIApi(urlWithCordaApiVersion, client),
                flowInfoClient = flowInfoClient ?: FlowInfoApi(urlWithCordaApiVersion, client),
                flowManagementClient = flowManagementClient ?: FlowManagementApi(urlWithCordaApiVersion, client),
                helloRestClient = helloRestClient ?: HelloRestApi(urlWithCordaApiVersion, client),
                hsmClient = hsmClient ?: HSMApi(urlWithCordaApiVersion, client),
                keyManagementClient = keyManagementClient ?: KeyManagementApi(urlWithCordaApiVersion, client),
                keyRotationClient = keyRotationClient ?: KeyRotationApi(urlWithCordaApiVersion, client),
                memberLookupClient = memberLookupClient ?: MemberLookupApi(urlWithCordaApiVersion, client),
                memberRegistrationClient = memberRegistrationClient ?: MemberRegistrationApi(urlWithCordaApiVersion, client),
                mgmAdminClient = mgmAdminClient ?: MGMAdminApi(urlWithCordaApiVersion, client),
                mgmClient = mgmClient ?: MGMApi(urlWithCordaApiVersion, client),
                networkClient = networkClient ?: NetworkApi(urlWithCordaApiVersion, client),
                rbacPermissionClient = rbacPermissionClient ?: RBACPermissionApi(urlWithCordaApiVersion, client),
                rbacRoleClient = rbacRoleClient ?: RBACRoleApi(urlWithCordaApiVersion, client),
                rbacUserClient = rbacUserClient ?: RBACUserApi(urlWithCordaApiVersion, client),
                virtualNodeClient = virtualNodeClient ?: VirtualNodeApi(urlWithCordaApiVersion, client),
                virtualNodeMaintenanceClient = virtualNodeMaintenanceClient ?: VirtualNodeMaintenanceApi(urlWithCordaApiVersion, client)
            )
        }

        /**
         * Create an instance of CordaRestClient with the given baseUrl, username and password.
         *
         * @param baseUrl The base URL of the Corda node as a URI.
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
            return createHttpClient(
                baseUrl = baseUrl.toString(),
                username = username,
                password = password,
                insecure = insecure,
                certificatesClient = certificatesClient,
                configurationClient = configurationClient,
                cpiClient = cpiClient,
                flowInfoClient = flowInfoClient,
                flowManagementClient = flowManagementClient,
                helloRestClient = helloRestClient,
                hsmClient = hsmClient,
                keyManagementClient = keyManagementClient,
                keyRotationClient = keyRotationClient,
                memberLookupClient = memberLookupClient,
                memberRegistrationClient = memberRegistrationClient,
                mgmAdminClient = mgmAdminClient,
                mgmClient = mgmClient,
                networkClient = networkClient,
                rbacPermissionClient = rbacPermissionClient,
                rbacRoleClient = rbacRoleClient,
                rbacUserClient = rbacUserClient,
                virtualNodeClient = virtualNodeClient,
                virtualNodeMaintenanceClient = virtualNodeMaintenanceClient
            )
        }

        // Prepare insecure settings for SSL
        private fun prepareInsecureSettings(): Pair<Array<TrustManager>, SSLContext> {
            val trustAllCerts: Array<TrustManager> = arrayOf(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return arrayOf()
                    }
                }
            )

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            return Pair(trustAllCerts, sslContext)
        }
    }
}
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
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


class CordaRestClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient
) {

    var certificatesClient = CertificateApi(baseUrl, httpClient)
    var configurationClient = ConfigurationApi(baseUrl, httpClient)
    var cpiClient = CPIApi(baseUrl, httpClient)
    var flowInfoClient = FlowInfoApi(baseUrl, httpClient)
    var flowManagementClient = FlowManagementApi(baseUrl, httpClient)
    var helloRestClient = HelloRestApi(baseUrl, httpClient)
    var hsmClient = HSMApi(baseUrl, httpClient)
    var keyManagementClient = KeyManagementApi(baseUrl, httpClient)
    var keyRotationClient = KeyRotationApi(baseUrl, httpClient)
    var memberLookupClient = MemberLookupApi(baseUrl, httpClient)
    var memberRegistrationClient = MemberRegistrationApi(baseUrl, httpClient)
    var mgmAdminClient = MGMAdminApi(baseUrl, httpClient)
    var mgmClient = MGMApi(baseUrl, httpClient)
    var networkClient = NetworkApi(baseUrl, httpClient)
    var rbacPermissionClient = RBACPermissionApi(baseUrl, httpClient)
    var rbacRoleClient = RBACRoleApi(baseUrl, httpClient)
    var rbacUserClient = RBACUserApi(baseUrl, httpClient)
    var virtualNodeClient = VirtualNodeApi(baseUrl, httpClient)
    var virtualNodeMaintenanceClient = VirtualNodeMaintenanceApi(baseUrl, httpClient)


    companion object {
        private const val API_VERSION = "/api/v5_3"

        /**
         * Create an instance of CordaRestClient with the given baseUrl, username and password.
         *
         * @param baseUrl The base URL of the Corda node.
         * @param username The username to authenticate with.
         * @param password The password to authenticate with.
         * @param insecure Whether to allow insecure connections. Default is false.
         */
        fun createHttpClient(
            baseUrl: String = "https://localhost:8888",
            username: String = "admin",
            password: String = "admin",
            insecure: Boolean = false
        ): CordaRestClient {

            val urlWithCordaApiVersion = baseUrl + API_VERSION

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
            ApiClient.apply {
                this.username = username
                this.password = password
            }
            val builder = ApiClient.builder.addInterceptor(
                Interceptor { chain ->
                    val newRequest = chain.request().newBuilder()
                        .addHeader("Authorization", Credentials.basic(username, password))
                        .build()
                    chain.proceed(newRequest)
                }
            )

            // Disable SSL verification if insecure is true
            if (insecure) {
                builder
                    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
            }

            val client = builder.build()
            return CordaRestClient(urlWithCordaApiVersion, client)
        }

        /**
         * Create an instance of CordaRestClient with the given baseUrl, username and password.
         *
         * @param baseUrl The base URL of the Corda node as a URI.
         * @param username The username to authenticate with.
         * @param password The password to authenticate with.
         * @param insecure Whether to allow insecure connections. Default is false.
         */
        fun createHttpClient(
            baseUrl: URI = URI.create("https://localhost:8888"),
            username: String = "admin",
            password: String = "admin",
            insecure: Boolean = false
        ): CordaRestClient {
            return createHttpClient(
                baseUrl = baseUrl.toString(),
                username = username,
                password = password,
                insecure = insecure
            )
        }
    }
}
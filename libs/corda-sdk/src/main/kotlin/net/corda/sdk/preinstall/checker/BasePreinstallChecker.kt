package net.corda.sdk.preinstall.checker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import net.corda.sdk.preinstall.data.SecretValues
import net.corda.sdk.preinstall.report.Report
import net.corda.utilities.VisibleForTesting
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.util.Base64

abstract class BasePreinstallChecker(
    protected val yamlFilePath: String
) : Checker {

    private var client: KubernetesClient = KubernetesClientBuilder().build()
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @VisibleForTesting
    internal val report = Report()

    class SecretException : Exception {
        constructor (message: String?) : super(message)
        constructor (message: String?, cause: Throwable?) : super(message, cause)
    }

    // parse a yaml file, and return an object of type T or null if there was an error
    inline fun <reified T> parseYaml(path: String): T {
        val file = File(path)

        if (!file.isFile) {
            throw FileNotFoundException("Could not read file at $path.")
        }

        val mapper: ObjectMapper = YAMLMapper()
        return mapper.readValue(file, T::class.java)
    }

    // get the credentials (.value) or credentials from a secret (.valueFrom.secretKeyRef...) from a SecretValues
    // object (or default values), and a namespace (if the credential is in a secret)
    @Suppress("ThrowsCount")
    fun getCredential(defaultValues: SecretValues?, values: SecretValues?, namespace: String?): String {
        if (!values?.valueFrom?.secretKeyRef?.name.isNullOrEmpty()) {
            return getSecret(values?.valueFrom?.secretKeyRef?.name!!, values.valueFrom.secretKeyRef.key, namespace)
        }
        if (!values?.value.isNullOrEmpty()) {
            return values?.value!!
        }
        return getCredential(defaultValues, namespace)
    }

    // get the credentials (.value) or credentials from a secret (.valueFrom.secretKeyRef...) from a SecretValues
    // object, and a namespace (if the credential is in a secret)
    @Suppress("ThrowsCount")
    fun getCredential(values: SecretValues?, namespace: String?): String {
        val secretName = values?.valueFrom?.secretKeyRef?.name
        if (secretName.isNullOrEmpty()) {
            val credential = values?.value
            if (credential.isNullOrEmpty()) {
                throw SecretException("No secretKeyRef name or value provided.")
            }
            return credential
        }
        return getSecret(secretName, values.valueFrom.secretKeyRef.key, namespace)
    }

    @Suppress("ThrowsCount")
    private fun getSecret(secretName: String, secretKey: String?, namespace: String?): String {
        if (secretKey.isNullOrEmpty()) {
            throw SecretException("No secret key provided with secret name $secretName.")
        }
        return try {
            val secret: Secret? = if (namespace != null) {
                checkNamespace(namespace)
                client.secrets().inNamespace(namespace).withName(secretName).get()
            } else {
                client.secrets().withName(secretName).get()
            }
            if (secret == null) {
                throw SecretException("Secret $secretName not found.")
            }
            val encoded = secret.data[secretKey] ?: throw SecretException("Secret $secretName has no key $secretKey.")
            String(Base64.getDecoder().decode(encoded))
        } catch (e: KubernetesClientException) {
            throw SecretException("Could not read secret $secretName with key $secretKey.", e)
        }
    }

    private fun checkNamespace(namespace: String) {
        val names = client.namespaces().list().items.map { item -> item.metadata.name }
        if (!names.contains(namespace)) {
            throw SecretException("Namespace $namespace does not exist.")
        }
    }
}

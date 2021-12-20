package net.corda.cli.application.services

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result
import net.corda.cli.api.services.HttpService
import org.yaml.snakeyaml.Yaml
import picocli.CommandLine
import java.io.FileInputStream

class HttpRpcService() : HttpService {

    @CommandLine.Option(names = ["-u", "--user"], description = ["User name"], required = true)
    override var username: String? = null

    @CommandLine.Option(names = ["-p", "--password"], description = ["Password"], required = true)
    override var password: String? = null

    @CommandLine.Option(names = ["-n", "--node-url"], description = ["The Swagger Url of the target Node."])
    override var url: String? = null

    private val data: Map<String, Any>
    private var urlToUse: String?

    init {
        val yaml = Yaml()
        data = yaml.load(FileInputStream(Files.profile))
        urlToUse = checkUrl()
    }

    override fun get(endpoint: String) {
        if (!urlToUse.isNullOrEmpty()) {
            val (_, response, result) = Fuel.get(urlToUse + endpoint)
                .authentication().basic(username.toString(), password.toString())
                .responseString()
            handleResult(result, response)
        }
    }

    override fun post(endpoint: String, jsonBody: String) {
        if (!urlToUse.isNullOrEmpty()) {
            val (_, response, result) = Fuel.post(urlToUse + endpoint)
                .jsonBody(jsonBody)
                .authentication().basic(username.toString(), password.toString())
                .responseString()
            handleResult(result, response)
        }
    }

    override fun patch(endpoint: String, jsonBody: String) {
        if (!urlToUse.isNullOrEmpty()) {
            val (_, response, result) = Fuel.patch(urlToUse + endpoint)
                .jsonBody(jsonBody)
                .authentication().basic(username.toString(), password.toString())
                .responseString()
            handleResult(result, response)
        }
    }

    override fun put(endpoint: String, jsonBody: String) {
        if (!urlToUse.isNullOrEmpty()) {
            val (_, response, result) = Fuel.put(urlToUse + endpoint)
                .jsonBody(jsonBody)
                .authentication().basic(username.toString(), password.toString())
                .responseString()
            handleResult(result, response)
        }
    }

    override fun delete(endpoint: String) {
        if (!urlToUse.isNullOrEmpty()) {
            val (_, response, result) = Fuel.delete(urlToUse + endpoint)
                .authentication().basic(username.toString(), password.toString())
                .responseString()
            handleResult(result, response)
        }
    }

    private fun handleResult(
        result: Result<String, FuelError>,
        response: Response
    ) {
        when (result) {
            is Result.Failure -> {
                if (response.responseMessage.isBlank()) {
                    println("${response.statusCode}: '${result.error.exception.message}', caused by: ${result.error.exception.javaClass}")
                } else {
                    println("${response.statusCode}: ${response.responseMessage}")
                }
            }
            is Result.Success -> {
                println(result.get())
            }
        }
    }

    private fun checkUrl(): String? {
        if (!data.containsKey("url") && url.isNullOrEmpty()) {
            println("A url must be supplied in either the profile yaml file, or as a parameter")
            return null
        } else if (!url.isNullOrEmpty()) {
            return url
        }
        return data["url"].toString()
    }
}


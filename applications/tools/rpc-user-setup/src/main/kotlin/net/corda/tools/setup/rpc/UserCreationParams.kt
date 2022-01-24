package net.corda.tools.setup.rpc

import net.corda.v5.base.util.days
import picocli.CommandLine.Option

internal class UserCreationParams {

    @Option(names = ["-l", "--login"], description = ["User login name."], required = true)
    var loginName = ""

    @Option(names = ["-p", "--password"], description = ["User password. Could be omitted with SSO authentication."])
    var password: String? = null

    @Option(names = ["-e", "--passwordExpiry"], description = ["Password expiry in ISO-8601 format. E.g. 'PT48H'. " +
            "Defaulted to 1 week."])
    var passwordExpiry: String = 7.days.toString()

    override fun toString(): String {
        return "Login Name: $loginName" +
                if (password == null) "" else ", Password: ***, Password expiry: $passwordExpiry"
    }
}
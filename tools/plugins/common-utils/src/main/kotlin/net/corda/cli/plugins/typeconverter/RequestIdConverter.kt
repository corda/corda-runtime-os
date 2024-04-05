package net.corda.cli.plugins.typeconverter

import net.corda.sdk.data.RequestId
import picocli.CommandLine

class RequestIdConverter : CommandLine.ITypeConverter<RequestId> {

    override fun convert(value: String): RequestId {
        return RequestId(value)
    }
}

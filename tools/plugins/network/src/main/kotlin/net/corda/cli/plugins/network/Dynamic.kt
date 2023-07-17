package net.corda.cli.plugins.network

import picocli.CommandLine.Command

@Command(
    name = "dynamic",
    subcommands = [
        OnboardMgm::class,
        OnBoardMember::class,
        AllowClientCertificate::class,
    ],
    description = ["On board a member."]
)
class Dynamic
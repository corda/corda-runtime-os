package net.corda.application.banner

import org.fusesource.jansi.Ansi
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@ServiceRanking(Int.MIN_VALUE)
@Component(service = [StartupBanner::class])
class CordaOsBanner : StartupBanner {
    override fun get(name: String, version: String): String {
        return Ansi.ansi()
                .newline().fgBrightRed().a(
            """   ______               __""").newline().a(
            """  / ____/     _________/ /___ _""").newline().a(
            """ / /     __  / ___/ __  / __ `/""").newline().a(
            """/ /___  /_/ / /  / /_/ / /_/ /""").newline().a(
            """\____/     /_/   \__,_/\__,_/""").reset().newline()
            .newline()
            .fgBrightDefault().bold().a("--- ${name} ($version) ---").newline()
            .newline().reset()
            .toString()
    }
}
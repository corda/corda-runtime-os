package net.corda.application.banner

import org.fusesource.jansi.Ansi
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@ServiceRanking(Int.MIN_VALUE)
@Component(service = [StartupBanner::class])
class CordaOsBanner : StartupBanner {
    override fun get(name: String, version: String): String {
        val messages = listOf(
            "The only distributed ledger that pays\nhomage to Pac Man in its logo.",
            "You know, I was a banker\nonce ... but I lost interest.",
            "It's not who you know, it's who you know\nknows what you know you know.",
            "It runs on the JVM because QuickBasic\nis apparently not 'professional' enough.",
            "\"It's OK computer, I go to sleep after\ntwenty minutes of inactivity too!\"",
            "It's kind of like a block chain but\ncords sounded healthier than chains.",
            "Computer science and finance together.\nYou should see our crazy Christmas parties!",
            "I met my bank manager yesterday and asked\nto check my balance ... he pushed me over!",
            "A banker left to their own devices may find\nthemselves .... a-loan! <applause>",
            "Whenever I go near my bank\nI get withdrawal symptoms",
            "There was an earthquake in California,\na local bank went into de-fault.",
            "I asked for insurance if the nearby\nvolcano erupted. They said I'd be covered.",
            "I had an account with a bank in the\nNorth Pole, but they froze all my assets",
            "Check your contracts carefully. The fine print\nis usually a clause for suspicion",
            "Some bankers are generous ...\nto a vault!",
            "What you can buy for a dollar these\ndays is absolute non-cents!",
            "Old bankers never die, they\njust... pass the buck",
            "I won $3M on the lottery so I donated a quarter\nof it to charity. Now I have $2,999,999.75.",
            "There are two rules for financial success:\n1) Don't tell everything you know.",
            "Top tip: never say \"oops\", instead\nalways say \"Ah, Interesting!\"",
            "Computers are useless. They can only\ngive you answers.  -- Picasso",
            "Regular naps prevent old age, especially\nif you take them whilst driving.",
            "Always borrow money from a pessimist.\nHe won't expect it back.",
            "War does not determine who is right.\nIt determines who is left.",
            "A bus stops at a bus station. A train stops at a\ntrain station. What happens at a workstation?",
            "I got a universal remote control yesterday.\nI thought, this changes everything.",
            "Did you ever walk into an office and\nthink, whiteboards are remarkable!",
            "The good thing about lending out your time machine\nis that you basically get it back immediately.",
            "I used to work in a shoe recycling\nshop. It was sole destroying.",
            "What did the fish say\nwhen he hit a wall? Dam.",
            "You should really try a seafood diet.\nIt's easy: you see food and eat it.",
            "I recently sold my vacuum cleaner,\nall it was doing was gathering dust.",
            "My professor accused me of plagiarism.\nHis words, not mine!",
            "Change is inevitable, except\nfrom a vending machine.",
            "If at first you don't succeed, destroy\nall the evidence that you tried.",
            "If at first you don't succeed, \nthen we have something in common!",
            "Moses had the first tablet that\ncould connect to the cloud.",
            "How did my parents fight boredom before the internet?\nI asked my 17 siblings and they didn't know either.",
            "Cats spend two thirds of their lives sleeping\nand the other third making viral videos.",
            "The problem with troubleshooting\nis that trouble shoots back.",
            "I named my dog 'Six Miles' so I can tell\npeople I walk Six Miles every day.",
            "People used to laugh at me when I said I wanted\nto be a comedian. Well they're not laughing now!",
            "My wife just found out I replaced our bed\nwith a trampoline; she hit the roof.",
            "My boss asked me who is the stupid one, me or him?\nI said everyone knows he doesn't hire stupid people.",
            "Don't trust atoms.\nThey make up everything.",
            "Keep the dream alive:\nhit the snooze button.",
            "Rest in peace, boiled water.\nYou will be mist.",
            "When I discovered my toaster wasn't\nwaterproof, I was shocked.",
            "Where do cryptographers go for\nentertainment? The security theatre.",
            "How did the Java programmer get rich?\nThey inherited a factory.",
            "Why did the developer quit his job?\nHe didn't get ar-rays.",
            "Quantum computer jokes are both\n funny and not funny at the same time.",
            "A mushroom walks into a bar, the bartender says we don't serve your kind here.\nWhy not, says the mushroom, I'm a fun guy!"
        )

        val (msg1, msg2) = messages.randomOrNull()!!.split('\n')

        return Ansi.ansi()
                .newline().fgBrightRed().a(
            """   ______               __""").newline().a(
            """  / ____/     _________/ /___ _""").newline().a(
            """ / /     __  / ___/ __  / __ `/         """).fgBrightBlue().a(msg1).newline().fgBrightRed().a(
            """/ /___  /_/ / /  / /_/ / /_/ /          """).fgBrightBlue().a(msg2).newline().fgBrightRed().a(
            """\____/     /_/   \__,_/\__,_/""").reset().newline()
                .newline().fgBrightDefault().bold().a("--- ${name} ($version) ---").newline()
                .newline().reset().toString()
    }
}
package net.corda.crypto.softhsm.impl

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

class CloudHSMTests {
    @Disabled("Needs a lot of manual set up and generalisaiton; see https://r3-cev.atlassian.net/wiki/spaces/CCD/pages/1638498581/AWS+Cloud+HSM")
    @Test
    fun `connect to CloudHSM bastion and wrap (encrypt) some data POC`() {
        // The AWS CloudHSM client only supports some Linux and Windows variants.
        // The easy way forward for local development is a set of Linux VMs on the same subnets
        // as the CloudHSMs. We therefore set up a "bastion" VM with the CloudHSM client.
        // This code explores the feasibility of accessing the CloudHSM via such a bastion machine.
        // If we want to do this for real, possibly only to facilitate local development, this logic
        // would need moving out of this test case.
        val jsch = JSch()
        jsch.addIdentity("${System.getProperty("user.home")}/sshid.pem")

        val session = jsch.getSession(
            "ec2-user",
            "ec2-54-76-8-55.eu-west-1.compute.amazonaws.com",
            22
        ) // update to your allocated public or VPN DNS
        val config = Properties()
        config["StrictHostKeyChecking"] = "no"
        session.setConfig(config)
        session.connect()
        val hsmPassword = String(
            Files.readAllBytes(Paths.get("${System.getProperty("user.home")}/hsmpassword.txt")),
            StandardCharsets.UTF_8
        ).trim()
        val wch = session.openChannel("sftp") as ChannelSftp
        wch.connect()
        wch.put("/home/ec2-user/incoming.txt").use {
            it.write("hello World 2".toByteArray())
        }
        run(
            session,
            "/opt/cloudhsm/bin/key_mgmt_util singlecmd loginHSM -u CU -s cu1 -p $hsmPassword aesWrapUnwrap -m 1 -f /home/ec2-user/incoming.txt -out /home/ec2-user/incoming.wrapped -w 6"
        )
        val rch = session.openChannel("sftp") as ChannelSftp
        rch.connect()
        println("rch $rch")
        val ostream = rch.get("/home/ec2-user/incoming.wrapped")
        if (ostream != null) ostream.use {
            val content = it.readAllBytes()
            System.out.println("encrypted to $content")
        }
    }

    private fun run(session: Session, command: String) {
        val ch = session.openChannel("exec") as ChannelExec
        ch.setCommand(command)
        ch.setInputStream(null)
        val stream = ch.inputStream
        ch.connect()
        val tmp = ByteArray(1024)
        while (true) {
            while (stream.available() > 0) {
                val i = stream.read(tmp, 0, 1024)
                System.out.println(String(tmp, 0, i))
            }
            Thread.sleep(10)
            if (ch.isClosed() && stream.available() == 0) break
        }
        val ec = ch.exitStatus
        println("exit code $ec")
    }
}
This page describes the MGM onboarding process, through which a membership group is created. To understand the basic concept behind the dynamic network, see the  [Membership](../wiki/Membership) wiki.

## Deploy Corda 5 to Kubernetes
To build and deploy Corda 5 to a Kubernetes cluster, follow the instructions for [Local development with Kubernetes](https://github.com/corda/corda-runtime-os/wiki/Local-development-with-Kubernetes).

## Set variables to be used in later commands
Set the P2P gateway host and port and the RPC API host and port. This may also vary depending on where you have deployed your cluster(s) and how you have forwarded the ports. If you are testing in a single cluster these values aren't important so you can set something like `localhost` and `8080`. If you need a multi cluster set up then this will need to be a valid p2p gateway host and port. For example, `corda-p2p-gateway-worker.corda-cluster-a` and `8080` where `corda-p2p-gateway-worker` is the name of the p2p gateway k8s service and `corda-cluster-a` is the namespace the corda cluster is deployed within. It is also possible to use an IP address e.g. `192.168.0.1` instead of a hostname for the gateway.
<details>
<summary>Bash</summary>

```bash
export RPC_HOST=localhost
export RPC_PORT=8888
export P2P_GATEWAY_HOST=corda-p2p-gateway-worker.corda-cluster-a
export P2P_GATEWAY_PORT=8080
```
</details>
<details>
<summary>PowerShell</summary>

(Using [PowerShell version 7](https://learn.microsoft.com/en-gb/powershell/scripting/install/installing-powershell-on-windows?view=powershell-7.3#msi))

```PowerShell
$RPC_HOST = "localhost"
$RPC_PORT = 8888
$P2P_GATEWAY_HOST = "corda-p2p-gateway-worker.corda-cluster-a"
$P2P_GATEWAY_PORT = 8080
$AUTH_INFO = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(("admin:admin" -f $username,$password)))
```
</details>


Set the RPC API URL. This may vary depending on where you have deployed your cluster(s) and how you have forwarded the ports.
<details>
<summary>Bash</summary>

```bash
export API_URL="https://$RPC_HOST:$RPC_PORT/api/v1"
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$API_URL="https://$RPC_HOST`:$RPC_PORT/api/v1"
```
</details>

Set the working directory for storing temporary files.
<details>
<summary>Bash</summary>

```bash
export WORK_DIR=~/Desktop/register-mgm
mkdir -p "$WORK_DIR"
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$WORK_DIR = "$HOME/register-mgm"
md $WORK_DIR -Force
```
</details>

Set the path to your local clone of `corda-runtime-os`
<details>
<summary>Bash</summary>

```bash
export RUNTIME_OS=~/dev/corda-runtime-os
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$RUNTIME_OS = "~/dev/corda-runtime-os"
```
</details>

## Select a Certificate Authority
We need a CA to create certificates for keys generated within Corda. This CA will be external to Corda. Initially, this is just for P2P TLS certificates, but in the future, this can also be used for session certificates. This root CA certificate in PEM format will need to be included later when onboarding the MGM. 

For the purposes of testing in a development environment, this page will describe the use of a fake CA which is a development tool within `corda-runtime-os`. If using a real CA for testing, sections relating to the fake CA can be skipped. Where relevant, this page will call out alternative steps to take if using a real CA rather than a fake CA.  

### Create a fake CA
_Note: Skip this step if using a real CA._

If you have previously done this, there is no need to regenerate. The existing CA is fine to reuse.

To create a fake CA under `/tmp/ca` (note that this will create the CA in a temporary location), we can use these commands:
<details>
<summary>Bash</summary>

```bash
cd "$RUNTIME_OS"
./gradlew :applications:tools:p2p-test:fake-ca:clean :applications:tools:p2p-test:fake-ca:appJar
java -jar ./applications/tools/p2p-test/fake-ca/build/bin/corda-fake-ca-5.0.0.0-SNAPSHOT.jar -m /tmp/ca -a RSA -s 3072 ca
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
cd $RUNTIME_OS
./gradlew :applications:tools:p2p-test:fake-ca:clean :applications:tools:p2p-test:fake-ca:appJar
java -jar ./applications/tools/p2p-test/fake-ca/build/bin/corda-fake-ca-5.0.0.0-SNAPSHOT.jar -m $env:TEMP\tmp\ca -a RSA -s 3072 ca
```
</details>

Output will be something like `Wrote CA root certificate to /tmp/ca/ca/root-certificate.pem`. You will need to use this again, so take note of the file location.

If you have created the CA previously, there is no need to repeat this each time. 

## Build and upload a CPI

For an MGM, the CPI only needs the `GroupPolicy` file, so only an empty CPB is necessary. For this you can use the MGM test CPB.
``` bash
cd "$RUNTIME_OS"
./gradlew testing:cpbs:mgm:build
cp testing/cpbs/mgm/build/libs/mgm-5.0.0.0-SNAPSHOT-package.cpb "$WORK_DIR"
```

Create GroupPolicy.json in this directory. This is the group policy file specifically for the MGM. It's a bootstrapping policy file that tells the virtual node how it syncs and is on board and that it should generate a new group ID when installed.
<details>
<summary>Bash</summary>

```bash
echo '{
  "fileFormatVersion" : 1,
  "groupId" : "CREATE_ID",
  "registrationProtocol" :"net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService",
  "synchronisationProtocol": "net.corda.membership.impl.synchronisation.MgmSynchronisationServiceImpl"
}' > "$WORK_DIR"/GroupPolicy.json
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
Add-Content $WORK_DIR/GroupPolicy.json @"
{
  "fileFormatVersion" : 1,
  "groupId" : "CREATE_ID",
  "registrationProtocol" :"net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService",
  "synchronisationProtocol": "net.corda.membership.impl.synchronisation.MgmSynchronisationServiceImpl"
}
"@
```
</details>

Build the CPI using the `corda-cli` packaging plugin, and pass in the MGM CPB, and group policy. See this [CorDapp Packaging](../wiki/CorDapp-Packaging) for more details. Be sure to use the above group policy and the MGM CPB when following that wiki instructions.

Upload the CPI.
<details>
<summary>Bash</summary>

```bash
export CPI_PATH = "<CPI PATH>"
curl --insecure -u admin:admin -F upload=@$CPI_PATH $API_URL/cpi/
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$CPI_PATH = "$WORK_DIR\mgm-5.0.0.0-SNAPSHOT-package.cpi"
$CPI_UPLOAD_RESPONSE = Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/cpi/" -Method Post -Form @{
    upload = Get-Item -Path $CPI_PATH
}
```
</details>

The returned identifier (for example `f0a0f381-e0d6-49d2-abba-6094992cef02`) is the `CPI ID`, use it below to get the checksum of the CPI.

<details>
<summary>Bash</summary>

```bash
export CPI_ID=<CPI ID>
curl --insecure -u admin:admin $API_URL/cpi/status/$CPI_ID
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$CPI_ID = $CPI_UPLOAD_RESPONSE.id
$CPI_STATUS_RESPONSE = Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/cpi/status/$CPI_ID"
```
</details>

The result contains the `cpiFileChecksum`. Save this for the next step.

## Create a virtual node for the MGM
<details>
<summary>Bash</summary>

```bash
export CPI_CHECKSUM=<CPI checksum>
curl --insecure -u admin:admin -d '{ "request": {"cpiFileChecksum": "'$CPI_CHECKSUM'", "x500Name": "C=GB, L=London, O=MGM"}}' $API_URL/virtualnode
```
Replace `<holding identity ID>` with the ID from the previous step (in `holdingIdentity.shortHash` for example: `58B6030FABDD`).
```
export MGM_HOLDING_ID=<holding identity ID>
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$VIRTUAL_NODE_RESPONSE = Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/virtualnode" -Method Post -Body (ConvertTo-Json @{
    request = @{
       cpiFileChecksum = $CPI_STATUS_RESPONSE.cpiFileChecksum
       x500Name = "C=GB, L=London, O=MGM"
    }
})

$MGM_HOLDING_ID = $VIRTUAL_NODE_RESPONSE.holdingIdentity.shortHash
```
</details>

## Assign soft HSM, generate session initiation and ecdh key pair
<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin -X POST $API_URL/hsm/soft/$MGM_HOLDING_ID/SESSION_INIT
curl --insecure -u admin:admin -X POST $API_URL/keys/$MGM_HOLDING_ID/alias/$MGM_HOLDING_ID-session/category/SESSION_INIT/scheme/CORDA.ECDSA.SECP256R1
```
The result contains `key ID` (e.g. 3B9A266F96E2), save this for use in subsequent steps.
```
export SESSION_KEY_ID=<session key ID>
```
</details>

<details>
<summary>PowerShell</summary>

```PowerShell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/hsm/soft/$MGM_HOLDING_ID/SESSION_INIT"
$SESSION_KEY_RESPONSE = Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/keys/$MGM_HOLDING_ID/alias/$MGM_HOLDING_ID-session/category/SESSION_INIT/scheme/CORDA.ECDSA.SECP256R1"
$SESSION_KEY_ID = $SESSION_KEY_RESPONSE.id
```
</details>

Note it is possible to use a certificate in addition to the session initiation key pair see [Session-Certificates](https://github.com/corda/corda-runtime-os/wiki/Session-Certificates).

<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin -X POST $API_URL/hsm/soft/$MGM_HOLDING_ID/PRE_AUTH
curl --insecure -u admin:admin -X POST $API_URL/keys/$MGM_HOLDING_ID/alias/$MGM_HOLDING_ID-auth/category/PRE_AUTH/scheme/CORDA.ECDSA.SECP256R1
```
The result contains `key ID` (e.g. 3B9A266F96E2), save this for use in subsequent steps.
```
export ECDH_KEY_ID=<ecdh key ID>
```
</details>

<details>
<summary>PowerShell</summary>

```PowerShell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/hsm/soft/$MGM_HOLDING_ID/PRE_AUTH"
$ECDH_KEY_RESPONSE = Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/keys/$MGM_HOLDING_ID/alias/$MGM_HOLDING_ID-auth/category/PRE_AUTH/scheme/CORDA.ECDSA.SECP256R1"
$ECDH_KEY_ID = $ECDH_KEY_RESPONSE.id
```
</details>

The schemes that can be used for ECDH key derivation are the following: CORDA.ECDSA.SECP256R1, CORDA.ECDSA.SECP256K1, CORDA.X25519 and CORDA.SM2

## Set up the TLS key pair and certificate
NOTE: This is only necessary if setting up on a new cluster. When using cluster level TLS, it is only necessary to do this once per cluster. The remainder of this section assumes you have not set this up already.

The following steps detail how to set up the TLS key pair and certificate for the cluster. There are a few important things to note here. These instructions will show how to set up P2P TLS at _cluster_ level meaning it's only possible to do this once for a cluster. If you have already set up a P2P TLS certificate for this cluster then skip this step as it only needs to be done once for a corda cluster if configuring at cluster level. It is also possible to set this at virtual node level, but we advise sticking with cluster level at the moment as it is the most commonly used approach.

We can create a TLS key pair in the P2P cluster level using the command:
<details>
<summary>Bash</summary>

```bash
curl -k -u admin:admin -X POST -H "Content-Type: application/json" $API_URL/keys/p2p/alias/p2p-TLS/category/TLS/scheme/CORDA.RSA
```
The endpoint should return the TLS key ID.
```bash
export TLS_KEY_ID=<TLS Key ID>
```
</details>

<details>
<summary>PowerShell</summary>

```PowerShell
$TLS_KEY_RESPONSE = Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/keys/p2p/alias/p2p-TLS/category/TLS/scheme/CORDA.RSA"
$TLS_KEY_ID = $TLS_KEY_RESPONSE.id
```
</details>

Next, we must create a certificate for the TLS key pair. Regardless of whether you are using the fake development tool as a CA, or using a real CA, you must first create a CSR. To generate a certificate request, we can run:
<details>
<summary>Bash</summary>

```bash
curl -k -u admin:admin  -X POST -H "Content-Type: application/json" -d '{"x500Name": "CN=CordaOperator, C=GB, L=London, O=Org", "subjectAlternativeNames": ["'$P2P_GATEWAY_HOST'"]}' $API_URL"/certificates/p2p/"$TLS_KEY_ID > "$WORK_DIR"/request1.csr
```
</details>

<details>
<summary>PowerShell</summary>

```PowerShell

Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/certificates/p2p/$TLS_KEY_ID" -Body (ConvertTo-Json @{
    x500Name = "CN=CordaOperator, C=GB, L=London, O=Org"
    subjectAlternativeNames = @($P2P_GATEWAY_HOST)
}) > $WORK_DIR/request1.csr
```
</details>

If you inspect the file `request1.csr` you should see something that resembles the following: 
```
-----BEGIN CERTIFICATE REQUEST-----
MIIDkjCCAfwCAQAwLjELMAkGA1UEBhMCR0IxDzANBgNVBAcTBkxvbmRvbjEOMAwG
A1UEAxMFQWxpY2UwggGiMA0GCSqGSIb3DQEBAQUAA4IBjwAwggGKAoIBgQChJ9CW
9bpnlKOg0OkRRSEPuo2CBMY4r9hDqgLPiadHqBh+QcXqZv23ftHDgFl7CnidG9xc
1t8oCcOYSd9SfLTuxINF+eUoBZ5n4Igj210z1kp20bGc31qi7chzNHiLgpDXrh0x
5AItxnHV+/grD4y3FxOxA6M0rGjMHsVWrxytTchEVN1cCEwUXWG0sjO3y4loctln
nBAQyjxo0au1K5r9jzjz8dorkgCALYuSpr4eyjRij+/DuStH9VHcz+XdFz9MlW/B
k4c8VzpqGhXs8w5UagnB81ZOmm+xoQOrELeZ1RPAEqCV8kAOO0xjfpTUIzOqz5bC
U0luWEM0Gw0BsNdrdaQtrpAs0mv3Rmq6pMD6jlXTqJ8NbravrAnP1DMqHZKKMWnU
PEyAuDJB3ndukJfRA03UpRmonvus1UXvheUgRG2f0RIbefvBLUqt+MBucgkUNQmf
qyPuD2XaCcOLfSZ+FTtMg2P2E4l2cAnhmzeGL3kHTvilm3ZWlWBUEfJ4BZ0CAwEA
AaAhMB8GCSqGSIb3DQEJDjESMBAwDgYDVR0PAQH/BAQDAgeAMAsGCSqGSIb3DQEB
DQOCAYEAYtt2Fpgelb81jAEDayLHKISuwRXThGQv3xuZtwxdiC3gWdJbV9H6IWzv
cmlxXUrnaju30eQ/LkB8tzuy++p2fIctO8y8Hiw743hddy6dEd21PxQHsNTAS5Ko
1yikmzzRwT5JwMY+EZDxDxXfYViq0xaZoHPbcr3LmwkipqRnZo4e2i5jUCQjFMYq
ZThLbl1NKHR98O2/akekmlpuGgtLFOLlSHYkZvZY7K1IEkLZAdbo4fhimDDxQg7T
v59nY3SSGbNirFlqz4UfAjKpPyV+UVgRNcFNxJyA6/eL/J8Wedb3zqat2utilLb7
6nicgQ0S3Xb5gPsTUXcsHRuD+FVJG+eJ1qEvh2srIZ57Nnjr9FTy6mqqN4Ln3g31
k9GLOv2kll+tFWjAZEDSRX2VqxkVOlEuKeGXcdrJ2EXz3G444A0wtiTgppwdy9Az
YCOEnQMQQUE3gXBax1UsQwl7M71it1/QuhtsBccLfX6rB8BNldwRibADPD16Y6PY
LwkiaZXc
-----END CERTIFICATE REQUEST-----
```

(You can view the details of the CSR using the `openssl req -text -noout -verify -in ./request1.csr` command)

At this point, if you are using a real CA, you should take this CSR to the CA in order to request a certificate to be issued. If using the fake CA dev tool, then use the fake CA created previously to sign the CSR and create a certificate:
<details>
<summary>Bash</summary>

```bash
cd "$RUNTIME_OS"
java -jar ./applications/tools/p2p-test/fake-ca/build/bin/corda-fake-ca-5.0.0.0-SNAPSHOT.jar -m /tmp/ca csr "$WORK_DIR"/request1.csr
cd "$WORK_DIR"
````
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
cd $RUNTIME_OS
java -jar ./applications/tools/p2p-test/fake-ca/build/bin/corda-fake-ca-5.0.0.0-SNAPSHOT.jar -m $env:TEMP\tmp\ca csr $WORK_DIR/request1.csr
cd $WORK_DIR
````
</details>

This should output the location of the signed certificate. For example, `Wrote certificate to /tmp/ca/request1/certificate.pem`

At this point, you should now have a certificate based on the CSR exported from Corda issued either by a real CA or by the fake CA tool. You need to upload the certificate chain to the Corda cluster. You can optionally omit the root certificate. To upload the certificate chain, run:
<details>
<summary>Bash</summary>

```bash
curl -k -u admin:admin -X PUT  -F certificate=@/tmp/ca/request1/certificate.pem -F alias=p2p-tls-cert $API_URL/certificates/cluster/p2p-tls
````
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Put -Uri "$API_URL/certificates/cluster/p2p-tls"  -Form @{
    certificate = Get-Item -Path $env:TEMP\tmp\ca\request1\certificate.pem
    alias = "p2p-tls-cert"
}

````
</details>

Note: If you upload a certificate chain consisting of more than one certificates, you need to ensure that `-----END CERTIFICATE-----` and `-----BEGIN CERTIFICATE-----` from the next certificate are separated by a new line and no empty spaces in between.

### Optional: Disable revocation checks
If the used CA has not been configured with revocation (e.g. via CRL or OCSP), you can disable revocation checks. By default, revocation checks are enabled. Note that the fake CA dev tool does not support revocation, so if you are using that you will need to disable revocation checks. Again, this only needs to be done once per cluster. First we need to get the current gateway configuration version.

<details>
<summary>Bash</summary>

```
curl --insecure -u admin:admin -X GET $API_URL/config/corda.p2p.gateway
```
Stores the version number (in the `version` field) from the response
```
export CONFIG_VERSION=<configuration version>
```

Using that config version, send the following request, which disables revocation checks for the gateway worker.
```
curl -k -u admin:admin -X PUT -d '{"section":"corda.p2p.gateway", "version":"'$CONFIG_VERSION'", "config":"{ \"sslConfig\": { \"revocationCheck\": { \"mode\": \"OFF\" }  }  }", "schemaVersion": {"major": 1, "minor": 0}}' $API_URL"/config"
```

</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$CONFIG_VERSION = (Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/config/corda.p2p.gateway").version
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Put -Uri "$API_URL/config" -Body (ConvertTo-Json -Depth 4 @{
    section = "corda.p2p.gateway"
    version = $CONFIG_VERSION
    config = @{
        sslConfig = @{
            revocationCheck = @{
                mode = "OFF"
            }
        }
    }
    schemaVersion = @{
        major = 1
        minor = 0
    }
})

```
</details>

### Optional: Enable mutual TLS
If you want to set the cluster to support mutual TLS you will need to set the gateway SSL configuration to support it.
_Note: Mutual TLS is set per cluster. It has to apply to all the groups that the cluster will host and all the clusters that those groups will be hosted on._

First we need to get the current gateway configuration version.
<details>
<summary>Bash</summary>

```
curl --insecure -u admin:admin -X GET $API_URL/config/corda.p2p.gateway
```
Stores the version number (in the `version` field) from the response
```
export CONFIG_VERSION=<configuration version>
```

Using that config version, send the following request, which disables revocation checks for the gateway worker.
```
curl -k -u admin:admin -X PUT -d '{"section":"corda.p2p.gateway", "version":"'$CONFIG_VERSION'", "config":"{ \"sslConfig\": { \"tlsType\": \"MUTUAL\"  }  }", "schemaVersion": {"major": 1, "minor": 0}}' $API_URL"/config"
```
_Note: This will overwrite the renovation check setting. to set both of them do:._
```
curl -k -u admin:admin -X PUT -d '{"section":"corda.p2p.gateway", "version":"'$CONFIG_VERSION'", "config":"{ \"sslConfig\": { \"tlsType\": \"MUTUAL\" , \"revocationCheck\": {\"mode\" : \"OFF\"} } }", "schemaVersion": {"major": 1, "minor": 0}}' $API_URL"/config"
```

</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$CONFIG_VERSION = (Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/config/corda.p2p.gateway").version
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Put -Uri "$API_URL/config" -Body (ConvertTo-Json -Depth 4 @{
    section = "corda.p2p.gateway"
    version = $CONFIG_VERSION
    config = @{
        sslConfig = @{
            tlsType = "MUTUAL"
        }
    }
    schemaVersion = @{
        major = 1
        minor = 0
    }
})

```
_Note: This will overwrite the renovation check setting. to set both of them do:._
```PowerShell
$CONFIG_VERSION = (Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/config/corda.p2p.gateway").version
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Put -Uri "$API_URL/config" -Body (ConvertTo-Json -Depth 4 @{
    section = "corda.p2p.gateway"
    version = $CONFIG_VERSION
    config = @{
        sslConfig = @{
            revocationCheck = @{
                mode = "OFF"
            }
            tlsType = "MUTUAL"
        }
    }
    schemaVersion = @{
        major = 1
        minor = 0
    }
})

```
</details>


## Build registration context
> Note: At the moment, a separate HSM category for ECDH key is not present. Until support is added - for the purpose of completing this process - the same session initiation key may be specified in both places.  

_Note: If the mutual TLS was enabled - make sure to set the field `corda.group.tls.type` to `Mutual`._

<details>
<summary>Bash</summary>

Replace `<TLS CA PEM Certificate>` with the PEM format certificate of the root CA generated earlier or the root certificate of the real CA in use for certificate issuance. When setting the certificate in the curl command, it should all be on one line, so replace new lines with `\n`.
```
export TLS_CA_CERT=$(cat /tmp/ca/ca/root-certificate.pem | awk '{printf "%s\\n", $0}')
export REGISTRATION_CONTEXT='{
  "corda.session.key.id": "'$SESSION_KEY_ID'",
  "corda.ecdh.key.id": "'$ECDH_KEY_ID'",
  "corda.group.protocol.registration": "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
  "corda.group.protocol.synchronisation": "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
  "corda.group.protocol.p2p.mode": "Authenticated_Encryption",
  "corda.group.key.session.policy": "Combined",
  "corda.group.pki.session": "NoPKI",
  "corda.group.pki.tls": "Standard",
  "corda.group.tls.type": "OneWay",
  "corda.group.tls.version": "1.3",
  "corda.endpoints.0.connectionURL": "https://'$P2P_GATEWAY_HOST':'$P2P_GATEWAY_PORT'",
  "corda.endpoints.0.protocolVersion": "1",
  "corda.group.truststore.tls.0" : "'$TLS_CA_CERT'"
}'
```
</details>

<details>
<summary>PowerShell</summary>

Replace the `TLS_CA_CERT_PATH` with the certificate path
```PowerShell
$TLS_CA_CERT_PATH = "$env:TEMP\tmp\ca\ca\root-certificate.pem"
$REGISTRATION_CONTEXT = @{
  'corda.session.key.id' =  $SESSION_KEY_ID
  'corda.ecdh.key.id' = $ECDH_KEY_ID
  'corda.group.protocol.registration' = "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService"
  'corda.group.protocol.synchronisation' = "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl"
  'corda.group.protocol.p2p.mode' = "Authenticated_Encryption"
  'corda.group.key.session.policy' = "Combined"
  'corda.group.pki.session' = "NoPKI"
  'corda.group.pki.tls' = "Standard"
  'corda.group.tls.version' = "1.3"
  'corda.group.tls.type' = "OneWay"
  'corda.endpoints.0.connectionURL' = "https://$P2P_GATEWAY_HOST`:$P2P_GATEWAY_PORT"
  'corda.endpoints.0.protocolVersion' = "1"
  'corda.group.truststore.tls.0'  =  [IO.File]::ReadAllText($TLS_CA_CERT_PATH)
}
```
</details>

## Register MGM
<details>
<summary>Bash</summary>

```bash
REGISTRATION_REQUEST='{"memberRegistrationRequest":{"action": "requestJoin", "context": '$REGISTRATION_CONTEXT'}}'
curl --insecure -u admin:admin -d "$REGISTRATION_REQUEST" $API_URL/membership/$MGM_HOLDING_ID
```

For example, this command would look something like this:
``` shell
curl --insecure -u admin:admin -d '{ "memberRegistrationRequest": { "action": "requestJoin", "context": {
  "corda.session.key.id": "D2FAF709052F",
  "corda.ecdh.key.id": "A9FDF319654B",
  "corda.group.protocol.registration": "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
  "corda.group.protocol.synchronisation": "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
  "corda.group.protocol.p2p.mode": "Authenticated_Encryption",
  "corda.group.key.session.policy": "Distinct",
  "corda.group.pki.session": "NoPKI",
  "corda.group.pki.tls": "Standard",
  "corda.group.tls.version": "1.3",
  "corda.endpoints.0.connectionURL": "https://corda-p2p-gateway-worker.corda-cluster-a:8080",
  "corda.endpoints.0.protocolVersion": "1",
  "corda.group.truststore.tls.0" : "-----BEGIN CERTIFICATE-----\nMIIBLjCB1aADAgECAgECMAoGCCqGSM49BAMCMBAxDjAMBgNVBAYTBVVLIENOMB4X\nDTIyMDgyMzA4MDUzN1oXDTIyMDkyMjA4MDUzN1owEDEOMAwGA1UEBhMFVUsgQ04w\nWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASG6ijAvbmaIaIwKpZZqTeKmMKfoOPb\ncCK/BqdtKXVTt5AjJtiP/Uoq+481UEQyaUZYXGf5rC1owjT40U2B71qdoyAwHjAP\nBgNVHRMBAf8EBTADAQH/MAsGA1UdDwQEAwIBrjAKBggqhkjOPQQDAgNIADBFAiEA\n1h6WEfdWUXSjBcenf5ycXPkYQQzI92I54q2WaVVjQHwCIEBk1ov/hYp9RCCDPnJx\nk8WgCZIyhFe0pEmow7MuI/Zk\n-----END CERTIFICATE-----"
} } }' https://localhost:8888/api/v1/membership/EF19BF67E77C
```

Alternatively, using jq:
```
curl --insecure -u admin:admin -d $(
jq -n '.memberRegistrationRequest.action="requestJoin"' | \
  jq --arg session_key_id $SESSION_KEY_ID '.memberRegistrationRequest.context."corda.session.key.id"=$session_key_id' | \
  jq --arg ecdh_key_id $ECDH_KEY_ID '.memberRegistrationRequest.context."corda.ecdh.key.id"=$ecdh_key_id' | \
  jq '.memberRegistrationRequest.context."corda.group.protocol.registration"="net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService"' | \
  jq '.memberRegistrationRequest.context."corda.group.protocol.synchronisation"="net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl"' | \
  jq '.memberRegistrationRequest.context."corda.group.protocol.p2p.mode"="Authenticated_Encryption"' | \
  jq '.memberRegistrationRequest.context."corda.group.key.session.policy"="Combined"' | \
  jq '.memberRegistrationRequest.context."corda.group.pki.session"="NoPKI"' | \
  jq '.memberRegistrationRequest.context."corda.group.pki.tls"="Standard"' | \
  jq '.memberRegistrationRequest.context."corda.group.tls.version"="1.3"' | \
  jq '.memberRegistrationRequest.context."corda.group.key.session.policy"="Combined"' | \
  jq --arg p2p_url "https://$P2P_GATEWAY_HOST:$P2P_GATEWAY_PORT" '.memberRegistrationRequest.context."corda.endpoints.0.connectionURL"=$p2p_url' | \
  jq '.memberRegistrationRequest.context."corda.endpoints.0.protocolVersion"="1"' | \
  jq --rawfile root_certicicate /tmp/ca/ca/root-certificate.pem '.memberRegistrationRequest.context."corda.group.truststore.tls.0"=$root_certicicate' \
) $API_URL/membership/$MGM_HOLDING_ID
```
</details>

<details>
<summary>PowerShell</summary>

```PowerShell
$REGISTER_RESPONSE = Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/membership/$MGM_HOLDING_ID" -Body (ConvertTo-Json -Depth 4 @{
    memberRegistrationRequest = @{
        action = "requestJoin"
        context = $REGISTRATION_CONTEXT
    }
})
$REGISTER_RESPONSE.registrationStatus
```
</details>

This should return a successful response with the status `SUBMITTED`. You can check if your MGM was onboarded successfully by checking the status of the registration request.  
<details>
<summary>Bash</summary>

```bash
export REGISTRATION_ID=<registration ID>
curl --insecure -u admin:admin -X GET $API_URL/membership/$MGM_HOLDING_ID/$REGISTRATION_ID
```
</details>

<details>
<summary>PowerShell</summary>

```PowerShell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/membership/$MGM_HOLDING_ID/${REGISTER_RESPONSE.registrationId}"
```
</details>
Within here you should see registration status as `APPROVED` if all went ok.

## Configure virtual node as network participant
At this point, the MGM virtual node must be configured with properties required for P2P messaging. 
<details>
<summary>Bash</summary>

```bash
curl -k -u admin:admin -X PUT -d '{"p2pTlsCertificateChainAlias": "p2p-tls-cert", "useClusterLevelTlsCertificateAndKey": true, "sessionKeyId": "'$SESSION_KEY_ID'"}' $API_URL/network/setup/$MGM_HOLDING_ID
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/network/setup/$MGM_HOLDING_ID" -Method Put -Body (ConvertTo-Json @{
    p2pTlsCertificateChainAlias = "p2p-tls-cert"
    useClusterLevelTlsCertificateAndKey = $true
    sessionKeyId = $SESSION_KEY_ID
})
```
</details>

This will set up the locally hosted identity, which is required in order for the P2P messaging to work.
`p2pTlsCertificateChainAlias` refers to the alias used when importing the TLS certificate.
`useClusterLevelTlsCertificateAndKey` is true if the TLS certificate and key are cluster-level certificates and keys.
`sessionKeyId` refers to the session key ID previously generated in this guide.


## Export group policy for group
Once the MGM is onboarded by following the above steps, the MGM can export a group policy file with the connection details of the MGM. The following endpoint outputs the full contents of the `GroupPolicy.json` file that should be packaged within the CPI for members.

<details>
<summary>Bash</summary>

```bash
mkdir -p "~/Desktop/register-member"
curl --insecure -u admin:admin -X GET $API_URL/mgm/$MGM_HOLDING_ID/info > "~/Desktop/register-member/GroupPolicy.json"
```
</details>

<details>
<summary>PowerShell</summary>

```PowerShell
md ~/register-member -Force
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/mgm/$MGM_HOLDING_ID/info" | ConvertTo-Json -Depth 4 > ~/register-member/GroupPolicy.json
```
</details>

From here, you can continue to the [Member onboarding](https://github.com/corda/corda-runtime-os/wiki/Member-Onboarding-(Dynamic-Networks)) to see how to use that group policy file to set up members in your network.





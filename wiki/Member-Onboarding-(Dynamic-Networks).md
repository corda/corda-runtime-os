This page describes the onboarding process for dynamic members. To understand the basic concept behind the dynamic network, see the  [Membership](../wiki/Membership) wiki.

Note that if you are using PowerShell to execute the commands in this guide, you must be running at least Powershell 7.3.0, which supports the `-SkipCertificateCheck` and `-Form` arguments used in some commands in this guide. See installation [here](https://learn.microsoft.com/en-gb/powershell/scripting/install/installing-powershell-on-windows?view=powershell-7.3#msi).

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
$API_URL = "https://$RPC_HOST`:$RPC_PORT/api/v1"
```
</details>

Set the working directory for storing temporary files.
<details>
<summary>Bash</summary>

```bash
export WORK_DIR=~/Desktop/register-member
mkdir -p $WORK_DIR
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$WORK_DIR = "$HOME/register-member"
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
### Optional: Disable revocation checks
If the used CA has not been configured with revocation (e.g. via CRL or OCSP), you can disable revocation checks. By default, revocation checks are enabled. Note that the fake CA dev tool does not support revocation, so if you are using that you will need to disable revocation checks. Again, this only needs to be done once per cluster. First we need to get the current gateway configuration version.
<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin -X GET $API_URL/config/corda.p2p.gateway
```
Stores the version number (`version`) from the response
```
export CONFIG_VERSION=<configuration version>
```

Using that config version, send the following request, which disables revocation checks for the gateway worker.
```
curl -k -u admin:admin -X PUT -d '{"section":"corda.p2p.gateway", "version":"'$CONFIG_VERSION'", "config":"{ \"sslConfig\": { \"revocationCheck\": { \"mode\": \"OFF\" }  }  }", "schemaVersion": {"major": 1, "minor": 0}}' $API_URL/config
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
_Note: Mutual TLS is set per cluster. It has to apply to all the groups that the cluster will host and all the clusters that those groups will be hosted on. One can not onboard a member unless the TLS type of the MGM cluster is align with the TLS type of the member cluster_

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


## Build and upload a CPI
A running MGM is required to onboard members. You can find the instruction on how to do so in the [MGM Onboarding](../wiki/MGM-Onboarding). If you followed those steps and saved the Group Policy file, you can skip the first two steps to export a group policy file.

<details>
<summary>Bash</summary>

Set the MGM properties.
```bash
export MGM_RPC_HOST=localhost
export MGM_RPC_PORT=8888
export MGM_API_URL="https://$MGM_RPC_HOST:$MGM_RPC_PORT/api/v1"
export MGM_HOLDING_ID=<MGM Holding ID>
```

Create a GroupPolicy.json by exporting it using the MGM. Copy this file into the directory created in the previous step.
```bash
curl --insecure -u admin:admin -X GET $MGM_API_URL/mgm/$MGM_HOLDING_ID/info > $WORK_DIR/GroupPolicy.json
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$MGM_RPC_HOST = "localhost"
$MGM_RPC_PORT = "8888"
$MGM_API_URL = "https://$MGM_RPC_HOST`:$MGM_RPC_PORT/api/v1"
$MGM_HOLDING_ID = <MGM Holding ID>
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$MGM_API_URL/mgm/$MGM_HOLDING_ID/info" | ConvertTo-Json -Depth 4 > $WORK_DIR/GroupPolicy.json
```
</details>

Build the CPI using the corda-cli packaging plugin, and pass in the Member CPB, and group policy. See this [CorDapp Packaging](https://github.com/corda/corda-runtime-os/wiki/CorDapp-Packaging) for more details. Be sure to use the above group policy and the CPB you require when following that wiki instructions.

Upload the CPI.
<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin -F upload=@$CPI_PATH $API_URL/cpi/
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$CPI_UPLOAD_RESPONSE = Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/cpi/" -Method Post -Form @{
    upload = Get-Item -Path $CPI_PATH
}
```
</details>

The returned identifier (e.g. the return will look like `{"id":"f0a0f381-e0d6-49d2-abba-6094992cef02"}` and the identifier is the id, or, using jq, one can run `CPI_ID=$(curl --insecure -u admin:admin -F upload=@$CPI_PATH $API_URL/cpi | jq -r '.id')`) ) is the `CPI ID`, use it below to get the checksum of the CPI.
<details>
<summary>Bash</summary>

```bash
export CPI_ID=<CPI ID>
curl --insecure -u admin:admin $API_URL/cpi/status/$CPI_ID
```
The result contains the `cpiFileChecksum`. Save this for the next step.
</details>

<details>
<summary>PowerShell</summary>

```PowerShell
$CPI_ID = $CPI_UPLOAD_RESPONSE.id
$CPI_STATUS_RESPONSE = Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/cpi/status/$CPI_ID"
```
</details>

## Create a virtual node for the member
Change the X500 name here for your case.
<details>
<summary>Bash</summary>

```bash
export CPI_CHECKSUM=<CPI checksum>
export X500_NAME="C=GB, L=London, O=Alice"
curl --insecure -u admin:admin -d '{"request": {"cpiFileChecksum": "'$CPI_CHECKSUM'", "x500Name": "'$X500_NAME'"}}' $API_URL/virtualnode
```
The result contains the `holdingIdentity.shortHash` (e.g. 58B6030FABDD), save this for use in subsequent steps.
Replace `<holding identity ID>` with the ID from the previous step.
```
export HOLDING_ID=<holding identity ID>
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$X500_NAME = "C=GB, L=London, O=Alice"
$VIRTUAL_NODE_RESPONSE = Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/virtualnode" -Method Post -Body (ConvertTo-Json @{
    request = @{
       cpiFileChecksum = $CPI_STATUS_RESPONSE.cpiFileChecksum
       x500Name = $X500_NAME
    }
})

$HOLDING_ID = $VIRTUAL_NODE_RESPONSE.holdingIdentity.shortHash
```
</details>

## Assign HSM and generate key pairs
<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin -X POST $API_URL/hsm/soft/$HOLDING_ID/SESSION_INIT
curl --insecure -u admin:admin -X POST $API_URL'/keys/'$HOLDING_ID'/alias/'$HOLDING_ID'-session/category/SESSION_INIT/scheme/CORDA.ECDSA.SECP256R1'
```
The result contains `session key ID` (e.g. `id` - 3B9A266F96E2); save this for use in subsequent steps.
```bash
export SESSION_KEY_ID=<session key ID>
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/hsm/soft/$HOLDING_ID/SESSION_INIT"
$SESSION_KEY_RESPONSE = Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/keys/$HOLDING_ID/alias/$HOLDING_ID-session/category/SESSION_INIT/scheme/CORDA.ECDSA.SECP256R1"
$SESSION_KEY_ID = $SESSION_KEY_RESPONSE.id
```
</details>

Note it is possible to use a certificate in addition to the session initiation key pair (see [Session-Certificates](../wiki/Session-Certificates)).

<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin -X POST $API_URL/hsm/soft/$HOLDING_ID/LEDGER
curl --insecure -u admin:admin -X POST $API_URL/keys/$HOLDING_ID/alias/$HOLDING_ID-ledger/category/LEDGER/scheme/CORDA.ECDSA.SECP256R1
```
The result contains `ledger key ID` (e.g. `id` for example 3B9A266F96E2); save this for subsequent steps.
```
export LEDGER_KEY_ID=<ledger key ID>
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/hsm/soft/$HOLDING_ID/LEDGER"
$LEDGER_KEY_RESPONSE = Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/keys/$HOLDING_ID/alias/$HOLDING_ID-ledger/category/LEDGER/scheme/CORDA.ECDSA.SECP256R1"
$LEDGER_KEY_ID = $LEDGER_KEY_RESPONSE.id
```
</details>

### Optional: Notary key
If you are onboarding a member as a notary, you will need to generate notary keys in a similar way as done for other key types. First create a HSM, then generate the key and store the ID.

<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin -X POST $API_URL/hsm/soft/$HOLDING_ID/NOTARY
curl --insecure -u admin:admin -X POST $API_URL/keys/$HOLDING_ID/alias/$HOLDING_ID-notary/category/NOTARY/scheme/CORDA.ECDSA.SECP256R1
```
The result contains `notary key ID` (e.g. `id` for example 3B9A266F96E2); save this for subsequent steps.
```
export NOTARY_KEY_ID=<notary key ID>
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/hsm/soft/$HOLDING_ID/NOTARY"
$LEDGER_KEY_RESPONSE = Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/keys/$HOLDING_ID/alias/$HOLDING_ID-notary/category/NOTARY/scheme/CORDA.ECDSA.SECP256R1"
$NOTARY_KEY_ID = $NOTARY_KEY_RESPONSE.id
```
</details>

## Set up the TLS key pair and certificate
NOTE: This is only necessary if setting up a member on a new cluster. If you are using the same cluster as the MGM, then it's likely you've already set up the TLS key/certificate for the cluster, and it is not necessary to do so again. When using cluster-level TLS, it is only necessary to do this once per cluster. The remainder of this section assumes you have not set this up already.

We have to take the same steps that we already did for setting up our MGM to have our locally hosted identities ready for P2P communication. If the fake CA tool was used when onboarding the MGM, it should be re-used for members. If a real CA was used for the MGM onboarding, then the same CA should be used for members. ([MGM Onboarding](https://github.com/corda/corda-runtime-os/wiki/MGM-Onboarding#create-a-fake-ca))

We can create a TLS key pair at the P2P cluster level using the command:
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


Next, we must create a certificate for the TLS key pair. First, create a CSR. This needs to be done regardless of whether you are using a real or fake CA. To generate a certificate request, we can run:
<details>
<summary>Bash</summary>

```bash
curl -k -u admin:admin  -X POST -H "Content-Type: application/json" -d '{"x500Name": "CN=CordaOperator, C=GB, L=London, O=Org", "subjectAlternativeNames": ["'$P2P_GATEWAY_HOST'"]}' $API_URL"/certificates/p2p/"$TLS_KEY_ID > $WORK_DIR/request2.csr
```
</details>

<details>
<summary>PowerShell</summary>

```PowerShell

Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/certificates/p2p/$TLS_KEY_ID" -Body (ConvertTo-Json @{
    x500Name = "CN=CordaOperator, C=GB, L=London, O=Org"
    subjectAlternativeNames = @($P2P_GATEWAY_HOST)
}) > $WORK_DIR/request2.csr
```
</details>

If you inspect the file `request1.csr`, you should see something that resembles the following: 
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

If using the fake CA tool, use that tool to sign the CSR and create a certificate:
<details>
<summary>Bash</summary>

```bash
cd $RUNTIME_OS
java -jar ./applications/tools/p2p-test/fake-ca/build/bin/corda-fake-ca-5.0.0.0-SNAPSHOT.jar -m /tmp/ca csr $WORK_DIR/request2.csr
cd $WORK_DIR
````
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
cd $RUNTIME_OS
java -jar ./applications/tools/p2p-test/fake-ca/build/bin/corda-fake-ca-5.0.0.0-SNAPSHOT.jar -m $env:TEMP\tmp\ca csr $WORK_DIR/request2.csr
cd $WORK_DIR
````
</details>
This should output the location of the signed certificate. For example, `Wrote certificate to /tmp/ca/request2/certificate.pem`

If using a real CA, take the CSR to the CA so that they can issue a certificate for that CSR.

At this point, you should now have a certificate based on the CSR exported from Corda issued either by a real CA or by the fake CA tool. You need to upload the certificate chain to the Corda cluster. You can optionally omit the root certificate. To upload the certificate chain, run:
<details>
<summary>Bash</summary>

```bash
curl -k -u admin:admin -X PUT  -F certificate=@/tmp/ca/request2/certificate.pem -F alias=p2p-tls-cert $API_URL/certificates/cluster/p2p-tls
````
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Put -Uri "$API_URL/certificates/cluster/p2p-tls"  -Form @{
    certificate = Get-Item -Path $env:TEMP\tmp\ca\request2\certificate.pem
    alias = "p2p-tls-cert"
}

````
</details>

Note: If you upload a certificate chain consisting of more than one certificates, you need to ensure that `-----END CERTIFICATE-----` and `-----BEGIN CERTIFICATE-----` from the next certificate are separated by a new line and no empty spaces in between.

## Allow mutual TLS certificate
If the cluster supports mutual TLS we need to allow the MGM to accept TLS connections with the created certificate. To do that we need to use the MGM mutual TLS allow list APIs.

<details>
<summary>Bash</summary>
Set the MGM properties:
```bash
export MGM_RPC_HOST=localhost
export MGM_RPC_PORT=8888
export MGM_API_URL="https://$MGM_RPC_HOST:$MGM_RPC_PORT/api/v1"
export MGM_HOLDING_ID=<MGM Holding ID>
```
And then allow the certificate subject:
```bash
curl -k -u admin:admin  -X PUT -s "$MGM_API_URL/mgm/$MGM_HOLDING_ID/mutual-tls/allowed-client-certificate-subjects/CN=CordaOperator,C=GB,L=London,O=Org"
```
</details>
<details>
<summary>PowerShell</summary>

Set the MGM properties:
```PowerShell
$MGM_RPC_HOST = "localhost"
$MGM_RPC_PORT = "8888"
$MGM_API_URL = "https://$MGM_RPC_HOST`:$MGM_RPC_PORT/api/v1"
$MGM_HOLDING_ID = <MGM Holding ID>
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$MGM_API_URL/mgm/$MGM_HOLDING_ID/info" | ConvertTo-Json -Depth 4 > $WORK_DIR/GroupPolicy.json
```
And then allow the certificate subject:
```PowerShell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$MGM_API_URL/mgm/$MGM_HOLDING_ID/mutual-tls/allowed-client-certificate-subjects/CN=CordaOperator,C=GB,L=London,O=Org" -Method Put
```

</details>


## Configure virtual node as network participant
At this point, the member virtual node must be configured with properties required for P2P messaging. The order is slightly different to MGM onboarding in that for members, we must do this before registering and for MGMs, it is the opposite.

<details>
<summary>Bash</summary>

```bash
curl -k -u admin:admin -X PUT -d '{"p2pTlsCertificateChainAlias": "p2p-tls-cert", "useClusterLevelTlsCertificateAndKey": true, "sessionKeyId": "'$SESSION_KEY_ID'"}' $API_URL/network/setup/$HOLDING_ID
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/network/setup/$HOLDING_ID" -Method Put -Body (ConvertTo-Json @{
    p2pTlsCertificateChainAlias = "p2p-tls-cert"
    useClusterLevelTlsCertificateAndKey = $true
    sessionKeyId = $SESSION_KEY_ID
})
```
</details>

This will set up the locally hosted identity required for the P2P messaging to work.
This will set up the locally hosted identity, which is required in order for the P2P messaging to work.
`p2pTlsCertificateChainAlias` refers to the alias used when importing the TLS certificate.
`useClusterLevelTlsCertificateAndKey` is true if the TLS certificate and key are cluster-level certificates and keys.
`sessionKeyId` refers to the session key ID previously generated in this guide.

## Build registration context
> The available names for signature-spec are viewable through `KeysRpcOps`. One of them is used as an example below. 

<details>
<summary>Bash</summary>

```bash
export REGISTRATION_CONTEXT='{
  "corda.session.key.id": "'$SESSION_KEY_ID'",
  "corda.session.key.signature.spec": "SHA256withECDSA",
  "corda.ledger.keys.0.id": "'$LEDGER_KEY_ID'",
  "corda.ledger.keys.0.signature.spec": "SHA256withECDSA",
  "corda.endpoints.0.connectionURL": "https://'$P2P_GATEWAY_HOST':'$P2P_GATEWAY_PORT'",
  "corda.endpoints.0.protocolVersion": "1"
}'
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$REGISTRATION_CONTEXT = @{
  'corda.session.key.id' =  $SESSION_KEY_ID
  'corda.session.key.signature.spec' = "SHA256withECDSA"
  'corda.ledger.keys.0.id' = $LEDGER_KEY_ID
  'corda.ledger.keys.0.signature.spec' = "SHA256withECDSA"
  'corda.endpoints.0.connectionURL' = "https://$P2P_GATEWAY_HOST`:$P2P_GATEWAY_PORT"
  'corda.endpoints.0.protocolVersion' = "1"
}
```
</details>

If registering a member as a notary service representative:
<details>
<summary>Bash</summary>

```bash
export REGISTRATION_CONTEXT='{
  "corda.session.key.id": "'$SESSION_KEY_ID'",
  "corda.session.key.signature.spec": "SHA256withECDSA",
  "corda.ledger.keys.0.id": "'$LEDGER_KEY_ID'",
  "corda.ledger.keys.0.signature.spec": "SHA256withECDSA",
  "corda.endpoints.0.connectionURL": "https://'$P2P_GATEWAY_HOST':'$P2P_GATEWAY_PORT'",
  "corda.endpoints.0.protocolVersion": "1",
  "corda.roles.0" : "notary",
  "corda.notary.service.name" : <An X500 name for the notary service>,
  "corda.notary.service.plugin" : "net.corda.notary.NonValidatingNotary"
  "corda.notary.keys.0.id": "'$NOTARY_KEY_ID'",
  "corda.notary.keys.0.signature.spec": "SHA256withECDSA"
}'
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$REGISTRATION_CONTEXT = @{
  'corda.session.key.id' =  $SESSION_KEY_ID
  'corda.session.key.signature.spec' = "SHA256withECDSA"
  'corda.ledger.keys.0.id' = $LEDGER_KEY_ID
  'corda.ledger.keys.0.signature.spec' = "SHA256withECDSA"
  'corda.endpoints.0.connectionURL' = "https://$P2P_GATEWAY_HOST`:$P2P_GATEWAY_PORT"
  'corda.endpoints.0.protocolVersion' = "1"
  'corda.roles.0' : "notary",
  'corda.notary.service.name' : <An X500 name for the notary service>,
  'corda.notary.service.plugin' : "net.corda.notary.NonValidatingNotary"
  'corda.notary.keys.0.id' = $NOTARY_KEY_ID
  'corda.notary.keys.0.signature.spec' = "SHA256withECDSA"
}
```
</details>

>__NOTE__: The name for the non-validating notary plugin has not yet been finalised, and is subject to change

## Register members

To register a member, run the following command.  
<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin -d '{ "memberRegistrationRequest": { "action": "requestJoin", "context": '$REGISTRATION_CONTEXT' } }' $API_URL/membership/$HOLDING_ID
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$RESGISTER_RESPONSE = Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/membership/$HOLDING_ID" -Body (ConvertTo-Json -Depth 4 @{
    memberRegistrationRequest = @{
        action = "requestJoin"
        context = $REGISTRATION_CONTEXT
    }
})
$RESGISTER_RESPONSE.registrationStatus

```
</details>
This will send a join request to the MGM, the response should be `SUBMITTED`. 
> Note: If you are using our Swagger UI, the Swagger example for registration context does not match expected. Please use this example:
```
{
  "memberRegistrationRequest":{
    "action":"requestJoin",
    "context": <registration context>
  }
}
```

This should return a successful response with status SUBMITTED. You can check if your member was onboarded successfully by checking the status of the registration request.

<details>
<summary>Bash</summary>

```bash
export REGISTRATION_ID=<registration ID>
curl --insecure -u admin:admin -X GET $API_URL/membership/$HOLDING_ID/$REGISTRATION_ID
```
</details>

<details>
<summary>PowerShell</summary>

```PowerShell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/membership/$HOLDING_ID/${RESGISTER_RESPONSE.registrationId}"
```
</details>

Within here, you should see registration status as APPROVED if all went ok.

After the registration, you can use the look-up functions provided by the `MemberLookupRpcOps` to make sure that your member can see other members and has `ACTIVE` membership status.
<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin -X GET $API_URL/members/$HOLDING_ID
```
</details>

<details>
<summary>PowerShell</summary>

```PowerShell
 Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/membership/$HOLDING_ID" | ConvertTo-Json -Depth 4
```
</details>

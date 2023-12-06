In certain cases an MGM may need to update its own member-provided context, for example after key rotation or after changes to its endpoint information. On the other hand, an MGM that previously tried to register but failed may wish to try again. MGM re-registration can be used in both these scenarios.

> Note: Key rotation is not currently supported.

The instructions on this page assume you have completed the [MGM Onboarding](https://github.com/corda/corda-runtime-os/wiki/MGM-Onboarding) steps.

# Updating Member-provided Context

Currently, updates to the member-provided context are limited to endpoint information only. Changes to other Corda platform properties are not supported at the moment.

An MGM may inspect its current member-provided context either by performing a member lookup, or by looking up its latest registration request.
For example, to look up MGM:
<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin -X GET $API_URL/members/$HOLDING_ID?O=MGM
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/members/$HOLDING_ID?O=MGM" | ConvertTo-Json -Depth 4
```
</details>  

Alternatively, to retrieve the registration request:
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
$REGISTRATION_ID = <registration ID>
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/membership/$HOLDING_ID/$REGISTRATION_ID"
```
</details>

> Note: To retrieve information about key pairs (e.g. key ID required for the registration context) belonging to a holding identity, use `curl --insecure -u admin:admin -X GET $API_URL/keys/$HOLDING_ID`.

# How To Re-register

## Step 1 - Re-register MGM

Consider an MGM who has previously registered successfully with the following registration context:
<details>
<summary>Bash</summary>

```bash
export REGISTRATION_CONTEXT='{
  "corda.session.keys.0.id": "'$SESSION_KEY_ID'",
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
  "corda.group.trustroot.tls.0" : "'$TLS_CA_CERT'"
}'
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$REGISTRATION_CONTEXT = @{
  'corda.session.keys.0.id': $SESSION_KEY_ID,
  'corda.ecdh.key.id': $ECDH_KEY_ID,
  'corda.group.protocol.registration': "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
  'corda.group.protocol.synchronisation': "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
  'corda.group.protocol.p2p.mode': "Authenticated_Encryption",
  'corda.group.key.session.policy': "Combined",
  'corda.group.pki.session': "NoPKI",
  'corda.group.pki.tls': "Standard",
  'corda.group.tls.type': "OneWay",
  'corda.group.tls.version': "1.3",
  'corda.endpoints.0.connectionURL': "https://$P2P_GATEWAY_HOST:$P2P_GATEWAY_PORT",
  'corda.endpoints.0.protocolVersion': "1",
  'corda.group.trustroot.tls.0' : $TLS_CA_CERT
}
```
</details>

The MGM now wishes to change their endpoint (e.g. specifically change the port to `8082`), and must re-register with the updated context:
<details>
<summary>Bash</summary>

```bash
export REGISTRATION_CONTEXT='{
  "corda.session.keys.0.id": "'$SESSION_KEY_ID'",
  "corda.ecdh.key.id": "'$ECDH_KEY_ID'",
  "corda.group.protocol.registration": "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
  "corda.group.protocol.synchronisation": "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
  "corda.group.protocol.p2p.mode": "Authenticated_Encryption",
  "corda.group.key.session.policy": "Combined",
  "corda.group.pki.session": "NoPKI",
  "corda.group.pki.tls": "Standard",
  "corda.group.tls.type": "OneWay",
  "corda.group.tls.version": "1.3",
  "corda.endpoints.0.connectionURL": "https://'$P2P_GATEWAY_HOST':8082",
  "corda.endpoints.0.protocolVersion": "1",
  "corda.group.trustroot.tls.0" : "'$TLS_CA_CERT'"
}'
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$REGISTRATION_CONTEXT = @{
  'corda.session.keys.0.id': $SESSION_KEY_ID,
  'corda.ecdh.key.id': $ECDH_KEY_ID,
  'corda.group.protocol.registration': "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
  'corda.group.protocol.synchronisation': "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
  'corda.group.protocol.p2p.mode': "Authenticated_Encryption",
  'corda.group.key.session.policy': "Combined",
  'corda.group.pki.session': "NoPKI",
  'corda.group.pki.tls': "Standard",
  'corda.group.tls.type': "OneWay",
  'corda.group.tls.version': "1.3",
  'corda.endpoints.0.connectionURL': "https://$P2P_GATEWAY_HOST:8082",
  'corda.endpoints.0.protocolVersion': "1",
  'corda.group.trustroot.tls.0' : $TLS_CA_CERT
}
```
</details>

> Note: The change in the endpoint field is used as an example for illustration purposes. If you actually want to update the endpoint, you will also need to ensure you have updated the p2p gateway's configuration to use new endpoint so that it's reachable by members. Below is an example on how you can do this:

<details>
<summary>Bash</summary>

```bash
export NEW_CONFIG='{
  "config": { "serversConfiguration":[{"hostAddress":"'$P2P_GATEWAY_HOST'","hostPort":8082,"urlPath":"/"}]},
  "schemaVersion": {
    "major": 1,
    "minor": 0
  },
  "section": "corda.p2p.gateway",
  "version": "'$LATEST_VERSION'"
}'
curl --insecure -u admin:admin -d "$NEW_CONFIG" -X 'PUT' $API_URL/config
```

</details>

<details>
<summary>PowerShell</summary>

```PowerShell
$CONFIG_VERSION = (Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/config/corda.p2p.gateway").version
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Put -Uri "$API_URL/config" -Body (ConvertTo-Json -Depth 4 @{
    section = "corda.p2p.gateway"
    version = $LATEST_VERSION
    config = @{
        serversConfiguration = @(@{
            hostAddress = $P2P_GATEWAY_HOST
            hostPort = 8082
            urlPath = "/"
        })
    }
    schemaVersion = @{
        major = 1
        minor = 0
    }
})

```
</details>

The updated context now contains the updated endpoint. The MGM can then send a re-registration request using the common registration/re-registration endpoint:
<details>
<summary>Bash</summary>

```bash
export REGISTRATION_REQUEST='{"memberRegistrationRequest":{"context": '$REGISTRATION_CONTEXT'}}'
curl --insecure -u admin:admin -d "$REGISTRATION_REQUEST" $API_URL/membership/$HOLDING_ID
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$RESGISTER_RESPONSE = Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/membership/$HOLDING_ID" -Body (ConvertTo-Json -Depth 4 @{
    memberRegistrationRequest = @{
        context = $REGISTRATION_CONTEXT
    }
})
$RESGISTER_RESPONSE.registrationStatus
```
</details>

This should return a successful response with status SUBMITTED. It will then be processed locally in the cluster, where the MGM is hosted. You can check if the request was approved by checking the status of the registration request.

## Step 2 - Distribute MGM's new Member Info to members

The MGM's `MemberInfo` is currently distributed via the CPI to the members of the network. 
As a result, after an MGM is re-registered their `MemberInfo` is not automatically distributed to the various members of the network.

In order for that to happen, the following steps need to be followed:
* the group policy is exported from the MGM cluster via the REST endpoint, which will now contain the new `MemberInfo`. You can refer to the end of the [MGM Onboarding](https://github.com/corda/corda-runtime-os/wiki/Member-Onboarding-(Dynamic-Networks)) on how to export the group policy.
* the new group policy is used to build a new CPI. You can refer to the [Member Onboarding]() page for how to do this.
* the new CPI is shared with the members of the network.
* the members upgrade to the new version of the CPI. You can refer to the [Upgrading a CPI page](https://docs.r3.com/en/platform/corda/5.1/deploying-operating/vnodes/upgrade-cpi.html) for how to do this.

At this point, the members in the network will see the new `MemberInfo` of the MGM.
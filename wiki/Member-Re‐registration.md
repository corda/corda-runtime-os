In certain cases a member may request to update its own member-provided context, for example after key rotation or after changes to its endpoint information. On the other hand, a member that previously tried to register but failed may wish to try again. Membership re-registration can be used in both these scenarios.

> Note: Key rotation is not currently supported.

The instructions on this page assume you have completed the [Dynamic network member registration](https://github.com/corda/corda-runtime-os/wiki/Member-Onboarding-(Dynamic-Networks)) steps.

# Updating Member-provided Context

Currently, updates to the member-provided context are limited to custom properties (keys with "ext." prefix) and endpoint information only. Changes to other Corda platform properties are not supported at the moment.

A member may inspect its current member-provided context either by performing a member lookup, or by looking up its latest registration request.
For example, to look up Alice:
<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin -X GET $API_URL/members/$HOLDING_ID?O=Alice
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/membership/$HOLDING_ID?O=Alice" | ConvertTo-Json -Depth 4
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

Consider a member who has previously registered successfully with the following member-provided context:
<details>
<summary>Bash</summary>

```bash
REGISTRATION_CONTEXT='{
  "corda.session.keys.0.id": "'$SESSION_KEY_ID'",
  "corda.session.keys.0.signature.spec": "SHA256withECDSA",
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
  'corda.session.keys.0.id' =  $SESSION_KEY_ID
  'corda.session.keys.0.signature.spec' = "SHA256withECDSA"
  'corda.ledger.keys.0.id' = $LEDGER_KEY_ID
  'corda.ledger.keys.0.signature.spec' = "SHA256withECDSA"
  'corda.endpoints.0.connectionURL' = "https://$P2P_GATEWAY_HOST`:$P2P_GATEWAY_PORT"
  'corda.endpoints.0.protocolVersion' = "1"
}
```
</details>

The member now wishes to add a custom property to its member-provided context, and must re-register with the updated context:
<details>
<summary>Bash</summary>

```bash
export REGISTRATION_CONTEXT='{
  "corda.session.keys.0.id": "'$SESSION_KEY_ID'",
  "corda.session.keys.0.signature.spec": "SHA256withECDSA",
  "corda.ledger.keys.0.id": "'$LEDGER_KEY_ID'",
  "corda.ledger.keys.0.signature.spec": "SHA256withECDSA",
  "corda.endpoints.0.connectionURL": "https://'$P2P_GATEWAY_HOST':'$P2P_GATEWAY_PORT'",
  "corda.endpoints.0.protocolVersion": "1",
  "ext.sample": "apple"
}'
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$REGISTRATION_CONTEXT = @{
  'corda.session.keys.0.id' =  $SESSION_KEY_ID
  'corda.session.keys.0.signature.spec' = "SHA256withECDSA"
  'corda.ledger.keys.0.id' = $LEDGER_KEY_ID
  'corda.ledger.keys.0.signature.spec' = "SHA256withECDSA"
  'corda.endpoints.0.connectionURL' = "https://$P2P_GATEWAY_HOST`:$P2P_GATEWAY_PORT"
  'corda.endpoints.0.protocolVersion' = "1",
  'ext.sample' = "apple"
}
```
</details>

The updated context contains the new custom property with the "ext." prefix. The member sends a re-registration request using the common registration/re-registration endpoint:
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

This will send the request to the MGM, which should return a successful response with status SUBMITTED. You can check if the request was approved by checking the status of the registration request.

After successful re-registration, you should be able to see the member's information containing the 'ext.sample' field in their
member-provided context. The member retains its most recent status in the group - for example, a suspended member will remain suspended after successful re-registration.

### Optional: Serial Number

"corda.serial" is a Corda platform property embedded in the MemberInfo of all members. It acts as the MemberInfo's version, and is incremented by 1 after each registration.

This serial number may be optionally included in the registration context, to specify which version of the member's information was intended to be updated. If no serial number is provided while re-registering, the platform will use the member's latest serial number by default. Only the latest MemberInfo version may be updated - requests with an older serial number are declined. It is recommended to provide the serial number to avoid unintentional updates, in case you have an outdated version of the MemberInfo.

<details>
<summary>Bash</summary>

```bash
export REGISTRATION_CONTEXT='{
  "corda.session.keys.0.id": "'$SESSION_KEY_ID'",
  "corda.session.keys.0.signature.spec": "SHA256withECDSA",
  "corda.ledger.keys.0.id": "'$LEDGER_KEY_ID'",
  "corda.ledger.keys.0.signature.spec": "SHA256withECDSA",
  "corda.endpoints.0.connectionURL": "https://'$P2P_GATEWAY_HOST':'$P2P_GATEWAY_PORT'",
  "corda.endpoints.0.protocolVersion": "1",
  "ext.sample": "apple",
  "corda.serial": "1"
}'
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$REGISTRATION_CONTEXT = @{
  'corda.session.keys.0.id' =  $SESSION_KEY_ID
  'corda.session.keys.0.signature.spec' = "SHA256withECDSA"
  'corda.ledger.keys.0.id' = $LEDGER_KEY_ID
  'corda.ledger.keys.0.signature.spec' = "SHA256withECDSA"
  'corda.endpoints.0.connectionURL' = "https://$P2P_GATEWAY_HOST`:$P2P_GATEWAY_PORT"
  'corda.endpoints.0.protocolVersion' = "1",
  'ext.sample' = "apple",
  'corda.serial' = "1"
}
```
</details>

The serial number may be retrieved from the MGM-provided context of the MemberInfo by performing a member lookup.
> Note: For first-time registration the serial number should be 0 if provided.

# Request Queue

If a member submits more than one registration request at the same time, the MGM will queue the requests and process them one by one, treating each subsequent request in the queue as a re-registration attempt.

If a member submits multiple re-registration requests with the same serial number, the first request will be processed (if the serial is valid), however the other requests will be declined because the serial number specified in those requests would be outdated after the first request is completed.

# MGM Admin API

The MGM can use the MGM Admin API to force decline an in-progress registration request that may be stuck or displaying some other unexpected behaviour.

<details>
<summary>Bash</summary>

```bash
REQUEST_ID=<REQUEST ID>
curl --insecure -u admin:admin -X POST $API_URL/mgmadmin/$MGM_HOLDING_ID/force-decline/$REQUEST_ID
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$REQUEST_ID = <REQUEST ID>
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/mgmadmin/$MGM_HOLDING_ID/force-decline/$REQUEST_ID" -Method POST
```
</details>

> Note: This endpoint should only be used under exceptional circumstances.
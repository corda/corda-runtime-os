It is possible to temporarily suspend a member of the group. 
Once a member has been suspended, flow communication between it and other members of the group will be blocked.
A suspended member performing a member lookup will not see updates for the other members, apart from the MGM.
It is not possible to suspend the MGM.

## Set variables to be used by other commands

Set the `API_URL` to the URL of the REST worker.
This may vary depending on where you have deployed your cluster and how you have forwarded the ports.
Set `MGM_HOLDING_ID` to the short hash of the MGM's Holding Identity.
Set `MEMBER_X500_NAME` to the X500 name of the member being suspended or re-activated.

<details>
<summary>Bash</summary>

For example:
```
REST_HOST=localhost
REST_PORT=8888
export API_URL="https://$REST_HOST:$REST_PORT/api/v5_1"
export MGM_HOLDING_ID="<MGM Holding ID>"
export MEMBER_X500_NAME="<Member X500 Name>"
```

</details>
<details>
<summary>PowerShell</summary>

```PowerShell
$REST_HOST = "localhost"
$REST_PORT = 8888
$API_URL = "https://$REST_HOST`:$REST_PORT/api/v5_1"
$MGM_HOLDING_ID = "<MGM Holding ID>"
$MEMBER_X500_NAME = "<Member X500 Name>"
$AUTH_INFO = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(("admin:admin" -f $username,$password)))
```
</details>

## Querying for Members

The member lookup REST endpoint can be used to query for all members with a certain status.
It is possible to query for members with status `ACTIVE` or `SUSPENDED`, by default only `ACTIVE` members are shown.
Multiple statuses can be queried for together.
For example to query for all members with status `SUSPENDED`:

<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin "$API_URL/members/$MGM_HOLDING_ID?statuses=SUSPENDED"
```

</details>

<details>
<summary>PowerShell</summary>

```powershell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)}`
   -Method Get -Uri $API_URL/members/$MGM_HOLDING_ID`?statuses=SUSPENDED
```

</details>

From this the serial number of each member's member information can be extracted from the `corda.serial` field inside the `mgmContext`.
This is used in the following APIs.
Only the MGM can query for `SUSPENDED` members.

## Suspending a Member

The `suspend` endpoint can be used to suspend a member of the group:

<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin -X 'POST' "$API_URL/mgm/$MGM_HOLDING_ID/suspend" -H 'Content-Type: application/json' \
 -d '{"x500Name": '\"$MEMBER_X500_NAME\"', "serialNumber": "<SERIAL NUMBER>"}'
```
</details>

<details>
<summary>PowerShell</summary>

```powershell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)}`
  -Method Post -Uri $API_URL/mgm/$MGM_HOLDING_ID/suspend -Body (ConvertTo-Json -Depth 1 @{
  x500Name = $MEMBER_X500_NAME; serialNumber = <SERIAL NUMBER>})
```
</details>

Where `<SERIAL NUMBER>` is the current serial number of the member information.
If the serial number doesn't match, then the REST method will return a 409 CONFLICT.
This can happen if another process has updated the member information, before the suspension operation.
The operator can, then re-query the member lookup REST endpoint and decide if they still want to proceed with the operation.

## Activating a Member

The `activate` endpoint can be used to re-activate a suspended member of the group:

<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin -X 'POST' "$API_URL/mgm/$MGM_HOLDING_ID/activate" -H 'Content-Type: application/json' \
 -d '{"x500Name": '\"$MEMBER_X500_NAME\"', "serialNumber": "<SERIAL NUMBER>"}'
```

</details>

<details>
<summary>PowerShell</summary>

```powershell
Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)}`
  -Method Post -Uri $API_URL/mgm/$MGM_HOLDING_ID/activate -Body (ConvertTo-Json -Depth 1 @{
  x500Name = $MEMBER_X500_NAME; serialNumber = <SERIAL NUMBER>})
```
</details>

Where `<SERIAL NUMBER>` is the current serial number of the member information.
If the serial number doesn't match, then the REST method will return a 409 CONFLICT.
This works in the same way as for [Suspending a Member](#suspending-a-member).
Once a member has been re-activated it flow communication between it and other members can resume.

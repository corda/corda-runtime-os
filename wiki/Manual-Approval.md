# Manual approval of registration requests

Membership groups in Corda 5 may be configured to approve (or decline) member registration requests manually. The MGM operator can configure the approval method for joining the group, so that requests satisfying pre-defined criteria would require manual approval, while others would be auto-approved. Manual approval presents the request to the MGM operator, and allows the operator to review the request before approving/declining it via the REST API. This applies to registration and re-registration requests alike. The configuration may be added at any point in time, and only affects future registration requests - previously approved members will not be required to re-register.


# How to configure a group for manual approval

Registration requests are evaluated according to regular expression-based rules submitted by the MGM operator. The proposed `MemberInfo` is compared with the previous (if any) `MemberInfo` to calculate the difference in their member contexts. This difference will be 100% in case of a first-time registration, since there will be no previous `MemberInfo` for that member known to the MGM. If any of the keys present in this `MemberInfo` difference match the regular expressions set by the MGM operator, the request will require manual approval. If there are no matches, the request is auto-approved.

**Add a group approval rule**

<details>
<summary>Bash</summary>

```bash
RULE_PARAMS='{"ruleParams":{"ruleRegex": "corda.*", "ruleLabel": "Review all changes to keys in the Corda namespace"}}'
curl --insecure -u admin:admin -d "$RULE_PARAMS" $API_URL/mgm/$MGM_HOLDING_ID/approval/rules
```

</details>

**View current group approval rules**

<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin $API_URL/mgm/$MGM_HOLDING_ID/approval/rules
```

</details>

**Delete a group approval rule**

Replace `<RULE ID>` with the ID of the rule to be deleted. The rule ID can be retrieved from the response of creating a rule, or from the response of the GET endpoint described previously.

<details>
<summary>Bash</summary>

```bash
RULE_ID=<RULE ID>
curl --insecure -u admin:admin -X DELETE $API_URL/mgm/$MGM_HOLDING_ID/approval/rules/$RULE_ID
```

</details>

# How to manually approve/decline requests

Requests which are pending manual approval are assigned `PENDING_MANUAL_APPROVAL` status.

**View requests pending manual approval**

<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin https://localhost:8888/api/v1/mgm/$MGM_HOLDING_ID/registrations
```

</details>

To optionally view requests from a specific member (e.g. C=GB, L=London, O=Alice) and/or include historic requests, use:

<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin 'https://localhost:8888/api/v1/mgm/$MGM_HOLDING_ID/registrations?requestsubjectx500name=C%3DGB%2C%20L%3DLondon%2C%20O%3DAlice&viewhistoric=true'
```

</details>

**Approve a request**
> Note: This only works with requests that are in `PENDING_MANUAL_APPROVAL` status.

Replace `<REQUEST ID>` with the ID of the registration request.

<details>
<summary>Bash</summary>

```bash
REQUEST_ID=<REQUEST ID>
curl --insecure -u admin:admin -X POST $API_URL/mgm/$MGM_HOLDING_ID/approve/$REQUEST_ID
```

</details>

**Decline a request**
> Note: This only works with requests that are in `PENDING_MANUAL_APPROVAL` status.

Replace `<REQUEST ID>` with the ID of the registration request.

<details>
<summary>Bash</summary>

```bash
REQUEST_ID=<REQUEST ID>
REASON='{"reason":{"reason": "test"}}'
curl --insecure -u admin:admin -d "$REASON" $API_URL/mgm/$MGM_HOLDING_ID/decline/$REQUEST_ID
```

</details>

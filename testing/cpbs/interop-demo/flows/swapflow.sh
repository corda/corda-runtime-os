#!/usr/bin/env bash
set -e

clientRequestId="$RANDOM"
curl --insecure -u admin:admin -X POST \
  "https://localhost:8888/api/v1/flow/$ALICE_HASH" \
  -H 'accept: application/json' -H 'Content-Type: application/json' \
  -d '{"clientRequestId": "'$clientRequestId'",
  "flowClassName": "com.r3.corda.demo.interop.tokens.workflows.IssueFlow",
  "requestBody": {
  "amount" : 100
}}}'
printf "\n"
sleep 15
curl_result=$(curl --insecure -u admin:admin -X GET "https://localhost:8888/api/v1/flow/$ALICE_HASH/$clientRequestId" -H 'accept: application/json')
echo $curl_result
printf "\n"

STATE_ID=$(echo $curl_result | jq -r .flowResult | jq .stateId | tr -d '"')

clientRequestId="$RANDOM"
curl --insecure -u admin:admin -X POST \
  "https://localhost:8888/api/v1/flow/$ALICE_HASH" \
  -H 'accept: application/json' -H 'Content-Type: application/json' \
  -d '{"clientRequestId": "'$clientRequestId'",
  "flowClassName": "com.r3.corda.demo.interop.tokens.workflows.interop.SwapFlow",
  "requestBody": {
  "stateId" : "'"$STATE_ID"'",
  "newOwner" : "C=GB, L=London, O=Bob",
  "interopGroupId" : "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08"
}}}'
printf "\n"
sleep 10
curl --insecure -u admin:admin -X GET "https://localhost:8888/api/v1/flow/$ALICE_HASH/$clientRequestId" -H 'accept: application/json'
printf "\n"
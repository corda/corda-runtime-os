#!/usr/bin/env bash
set -e

clientRequestId="$RANDOM"
curl --insecure -u admin:admin -X POST \
   "https://localhost:8888/api/v1/flow/$ALICE_HASH" \
   -H 'accept: application/json' -H 'Content-Type: application/json' \
   -d '{"clientRequestId": "'$clientRequestId'",
   "flowClassName": "com.r3.corda.demo.interop.tokens.workflows.interop.SimpleReserveTokensFlow",
   "requestBody": {
      "interopGroupId" : "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08",
      "alias" : "C=GB, L=London, O=Bob Alias",
      "facadeId" : "org.corda.interop/platform/tokens/v1.0",
      "payload" : "'"$STATE_ID"'"
}}}'
printf "\n"
sleep 10
curl --insecure -u admin:admin -X GET "https://localhost:8888/api/v1/flow/$ALICE_HASH/$clientRequestId" -H 'accept: application/json'
printf "\n"
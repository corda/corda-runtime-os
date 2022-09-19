#!/usr/bin/env bash

while true;
	do kubectl --namespace "${1}" port-forward "${2}" "${3}" 2>&1
done

{{/*
 Create an Istio Sidecar for the rest worker
 */}}
{{- define "corda.istioRestWorkerSidecar" -}}
{{- if .Capabilities.APIVersions.Has "networking.istio.io/v1beta1/Sidecar" -}}
{{- $worker := "rest" }}
{{- $port := (index .Values.workers $worker).service.port }}
{{- $workerName := printf "%s-%s-worker" ( include "corda.fullname" $ ) ( include "corda.workerTypeKebabCase" $worker ) }}
---
apiVersion: networking.istio.io/v1beta1
kind: Sidecar
metadata:
  name: {{ $workerName | quote }}
  annotations:
    "helm.sh/hook": pre-install
spec:
  workloadSelector:
    labels:
      {{- include "corda.workerSelectorLabels" ( list . $worker ) | nindent 6 }}
  ingress:
    - port:
        number: {{ $port }}
        protocol: HTTPS
        name: https
{{- end -}}
{{- end -}}

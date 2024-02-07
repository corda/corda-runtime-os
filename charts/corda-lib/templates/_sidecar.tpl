{{/*
 Create an Istio Sidecar for the rest worker
 */}}
{{- define "corda.istioRestWorkerSidecar" -}}
{{- if .Capabilities.APIVersions.Has "networking.istio.io/v1beta1/Sidecar" -}}
---
apiVersion: networking.istio.io/v1beta1
kind: Sidecar
metadata:
  name: https
spec:
  workloadSelector:
    labels:
      {{- include "corda.workerSelectorLabels" ( list . "rest" ) | nindent 6 }}
  ingress:
    - port:
        number: {{ .Values.workers.rest.service.port }}
        protocol: HTTPS
        name: https
{{- end -}}
{{- end -}}

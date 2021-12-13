{{- define "imagePullSecretAuthEncoded" -}}
{{- printf "%s:%s" .Values.configuration.kubernetes.imagePullSecret.username .Values.configuration.kubernetes.imagePullSecret.password | b64enc -}}
{{- end -}}

{{- define "dockerConfigJsonEncoded" -}}
{{- printf "{ \"auths\": { \"*.software.r3.com\": { \"auth\": \"%s\" }}}" (include "imagePullSecretAuthEncoded" .) | b64enc -}}
{{- end -}}

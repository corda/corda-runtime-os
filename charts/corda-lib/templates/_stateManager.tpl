{{/*
    State Manager Named Templates
    Most of the templates verify the value set for the [workerName.stateManager.db.host] property as that's the main
    indication of whether the user has configured the stateManager for the individual worker or not. When not configured,
    the templates use the cluster database details by default.
*/}}

{{/* State Manager Database Type */}}
{{- define "corda.stateManagerDbType" -}}
{{- $ := index . 0 }}
{{- $workerConfig := index . 1 }}
{{-     if $workerConfig.stateManager.db.host -}}
{{-         $workerConfig.stateManager.db.type -}}
{{-     else -}}
{{-         include "corda.clusterDbType" . -}}
{{-     end -}}
{{- end -}}

{{/* State Manager Database Host */}}
{{- define "corda.stateManagerDbHost" -}}
{{- $ := index . 0 }}
{{- $workerConfig := index . 1 }}
{{-     if $workerConfig.stateManager.db.host -}}
{{-         $workerConfig.stateManager.db.host -}}
{{-     else -}}
{{-         $.Values.db.cluster.host -}}
{{-     end -}}
{{- end -}}


{{/* State Manager Database Port */}}
{{- define "corda.stateManagerDbPort" -}}
{{- $ := index . 0 }}
{{- $workerConfig := index . 1 }}
{{-     if $workerConfig.stateManager.db.host -}}
{{-         $workerConfig.stateManager.db.port -}}
{{-     else }}
{{-         include "corda.clusterDbPort" $ -}}
{{-     end }}
{{- end -}}


{{/* State Manager Database Name */}}
{{- define "corda.stateManagerDbName" -}}
{{- $ := index . 0 }}
{{- $workerConfig := index . 1 }}
{{-     if $workerConfig.stateManager.db.host -}}
{{-         $workerConfig.stateManager.db.name -}}
{{-     else }}
{{-         include "corda.clusterDbName" $ -}}
{{-     end }}
{{- end -}}


{{/* State Manager JDBC URL */}}
{{- define "corda.stateManagerJdbcUrl" -}}
{{- $ := index . 0 }}
{{- $workerConfig := index . 1 }}
{{- if $workerConfig.stateManager.db.host -}}
jdbc:{{- include "corda.stateManagerDbType" (list $ $workerConfig) -}}://{{- $workerConfig.stateManager.db.host -}}:{{- include "corda.stateManagerDbPort" (list $ $workerConfig) -}}/{{- include "corda.stateManagerDbName" (list $ $workerConfig) -}}
{{- else -}}
jdbc:{{- include "corda.clusterDbType" $ -}}://{{- $.Values.db.cluster.host -}}:{{- include "corda.clusterDbPort" $ -}}/{{- include "corda.clusterDbName" $ -}}
{{- end -}}
{{- end -}}


{{/* State Manager Default Worker Secret Name */}}
{{- define "corda.stateManagerDefaultSecretName" -}}
{{- $ := index . 0 }}
{{- $workerKey := index . 1 }}
{{- $workerName := printf "%s-worker" ( include "corda.workerTypeKebabCase" $workerKey ) }}
{{- printf "%s-%s-state-manager-secret" (include "corda.fullname" $) $workerName  }}
{{- end -}}


{{/* State Manager Default Worker Secret Name */}}
{{- define "corda.stateManagerDefaultBootSecretName" -}}
{{- $ := index . 0 }}
{{- $workerKey := index . 1 }}
{{- $workerName := printf "%s-worker" ( include "corda.workerTypeKebabCase" $workerKey ) }}
{{- printf "%s-%s-state-manager-boot-secret" (include "corda.fullname" $) $workerName  }}
{{- end -}}


{{- define "corda.bootstrapStateManagerDbEnv" -}}
{{- $ := index . 0 -}}
{{- $worker := index . 1 -}}
{{- $workerKey := index . 2 -}}
{{- $bootConfig := index . 3 -}}
- name: STATE_MANAGER_PGUSER
  valueFrom:
    secretKeyRef:
      {{- if $worker.stateManager.db.host }}
      {{-   if $bootConfig.username.valueFrom.secretKeyRef.name }}
      name: {{ $bootConfig.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required (printf "Must specify bootstrap.db.stateManager.%s.username.valueFrom.secretKeyRef.key" $workerKey) $bootConfig.username.valueFrom.secretKeyRef.key | quote }}
      {{-   else }}
      name: {{ include "corda.stateManagerDefaultBootSecretName" ( list $ $workerKey ) | quote }}
      key: "username"
      {{-  end }}
      {{- else }}
      {{- include "corda.bootstrapClusterPgUser" $ | nindent 6 }}
      {{- end }}
- name: STATE_MANAGER_PGPASSWORD
  valueFrom:
    secretKeyRef:
      {{- if $worker.stateManager.db.host }}
      {{-   if $bootConfig.password.valueFrom.secretKeyRef.name }}
      name: {{ $bootConfig.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required (printf "Must specify bootstrap.db.stateManager.%s.password.valueFrom.secretKeyRef.key" $workerKey) $bootConfig.password.valueFrom.secretKeyRef.key | quote }}
      {{-   else }}
      name: {{ include "corda.stateManagerDefaultBootSecretName" ( list $ $workerKey ) | quote }}
      key: "password"
      {{-  end }}
      {{- else }}
      {{- include "corda.bootstrapClusterPgPassword" $ | nindent 6 }}
      {{- end }}
{{- end -}}


{{/* State Manager Database Credentials Environment Variables */}}
{{- define "corda.stateManagerDbEnv" -}}
{{- $ := index . 0 -}}
{{- $worker := index . 1 -}}
{{- $workerKey := index . 2 -}}
- name: STATE_MANAGER_USERNAME
  valueFrom:
    secretKeyRef:
      {{- if $worker.stateManager.db.host }}
      {{-   if $worker.stateManager.db.username.valueFrom.secretKeyRef.name }}
      name: {{ $worker.stateManager.db.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required (printf "Must specify workers.%s.stateManager.db.username.valueFrom.secretKeyRef.key" $workerKey) $worker.stateManager.db.username.valueFrom.secretKeyRef.key | quote }}
      {{-   else }}
      name: {{ include "corda.stateManagerDefaultSecretName" ( list $ $workerKey ) | quote }}
      key: "username"
      {{-  end }}
      {{- else }}
      {{- include "corda.clusterDbUsername" $ | nindent 6 }}
      {{- end }}
- name: STATE_MANAGER_PASSWORD
  valueFrom:
    secretKeyRef:
      {{- if $worker.stateManager.db.host }}
      {{-   if $worker.stateManager.db.password.valueFrom.secretKeyRef.name }}
      name: {{ $worker.stateManager.db.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required (printf "Must specify workers.%s.stateManager.db.password.valueFrom.secretKeyRef.key" $workerKey) $worker.stateManager.db.password.valueFrom.secretKeyRef.key | quote }}
      {{-   else }}
      name: {{ include "corda.stateManagerDefaultSecretName" ( list $ $workerKey ) | quote }}
      key: "password"
      {{-  end }}
      {{- else }}
      {{- include "corda.clusterDbPassword" $ | nindent 6 }}
      {{- end }}
{{- end -}}


{{/* State Manager Containers to Create & Apply Database Schemas Within The Bootstrap Job */}}
{{- define "corda.bootstrapStateManagerDb" -}}
{{- $ := index . 0 -}}
{{- $workerKey := index . 1 -}}
{{- $authConfig := index . 2 -}}
{{- $worker := (index $.Values.workers $workerKey) -}}
{{- $workerName := printf "%s-worker" ( include "corda.workerTypeKebabCase" $workerKey ) -}}
{{- with index . 0 -}}
{{/* We use two init-containers for serial execution to prevent issues at applying the same Liquibase files at the same time (developer use case where all workers use the same state manager database)  */}}
        - name: generate-state-manager-{{ $workerName }}
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          env:
            {{- include "corda.bootstrapCliEnv" . | nindent 12 }}
            {{- include "corda.bootstrapStateManagerDbEnv" ( list $ $worker $workerKey $authConfig ) | nindent 12 }}
          command: [ 'sh', '-c', '-e' ]
          args:
            - |
              #!/bin/sh
              set -ev

              echo 'Generating State Manager DB specification for {{ $workerName }}...'
              STATE_MANAGER_JDBC_URL="{{- include "corda.stateManagerJdbcUrl" ( list . $worker ) -}}"
              mkdir /tmp/stateManager-{{ $workerKey }}
              java -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j2.debug=false -jar /opt/override/cli.jar database spec \
                -s "statemanager" -g "statemanager:state_manager" \
                -u "${STATE_MANAGER_PGUSER}" -p "${STATE_MANAGER_PGPASSWORD}" \
                --jdbc-url "${STATE_MANAGER_JDBC_URL}" \
                -c -l /tmp/stateManager-{{ $workerKey }}
              echo 'Generating State Manager DB specification for {{ $workerName }}... Done'
          workingDir: /tmp
          volumeMounts:
            - mountPath: /tmp
              name: temp
            {{- include "corda.log4jVolumeMount" . | nindent 12 }}
        - name: apply-state-manager-{{ $workerName }}
          image: {{ include "corda.bootstrapDbClientImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          env:
            - name: STATE_MANAGER_DB_HOST
              value: {{ include "corda.stateManagerDbHost" ( list . $worker ) | quote }}
            - name: STATE_MANAGER_DB_PORT
              value: {{ include "corda.stateManagerDbPort" ( list . $worker ) | quote }}
            - name: STATE_MANAGER_DB_NAME
              value: {{ include "corda.stateManagerDbName" ( list . $worker ) | quote }}
            {{- include "corda.stateManagerDbEnv" ( list $ $worker $workerKey ) | nindent 12 }}
            {{- include "corda.bootstrapStateManagerDbEnv" ( list $ $worker $workerKey $authConfig ) | nindent 12 }}
          command: [ 'sh', '-c', '-e' ]
          args:
            - |
              #!/bin/sh
              set -ev
              echo 'Applying State Manager Specification for {{ $workerName }}...'
              export PGPASSWORD="${STATE_MANAGER_PGPASSWORD}"
              find /tmp/stateManager-{{ $workerKey }} -iname "*.sql" | xargs printf -- ' -f %s' | xargs psql -v ON_ERROR_STOP=1 -h "${STATE_MANAGER_DB_HOST}" -p "${STATE_MANAGER_DB_PORT}" -U "${STATE_MANAGER_PGUSER}" --dbname "${STATE_MANAGER_DB_NAME}"
              echo 'Applying State Manager Specification for {{ $workerName }}... Done!'

              echo 'Creating users and granting permissions for State Manager in {{ $workerName }}...'
              psql -v ON_ERROR_STOP=1 -h "${STATE_MANAGER_DB_HOST}" -p "${STATE_MANAGER_DB_PORT}" -U "${STATE_MANAGER_PGUSER}" "${STATE_MANAGER_DB_NAME}" << SQL
                DO \$\$ BEGIN IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${STATE_MANAGER_USERNAME}') THEN RAISE NOTICE 'Role "${STATE_MANAGER_USERNAME}" already exists'; ELSE CREATE USER "${STATE_MANAGER_USERNAME}" WITH ENCRYPTED PASSWORD '${STATE_MANAGER_PASSWORD}'; END IF; END \$\$;
                GRANT USAGE ON SCHEMA STATE_MANAGER TO "${STATE_MANAGER_USERNAME}";
                GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA STATE_MANAGER TO "${STATE_MANAGER_USERNAME}";
                GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA STATE_MANAGER TO "${STATE_MANAGER_USERNAME}";
                ALTER ROLE "${STATE_MANAGER_USERNAME}" SET search_path TO STATE_MANAGER;
              SQL

              echo 'Creating users and granting permissions for State Manager in {{ $workerName }}... Done!'
          volumeMounts:
            - mountPath: /tmp
              name: temp
{{- end }}
{{- end }}

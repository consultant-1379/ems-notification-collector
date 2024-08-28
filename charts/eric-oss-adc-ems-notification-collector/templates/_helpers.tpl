{{/* vim: set filetype=mustache: */}}

{{/*
The mainImage path (DR-D1121-067)
*/}}
{{- define "eric-oss-adc-ems-notification-collector.mainImagePath" }}
    {{- $productInfo := fromYaml (.Files.Get "eric-product-info.yaml") -}}
    {{- $registryUrl := $productInfo.images.emsNotificationCollector.registry -}}
    {{- $repoPath := $productInfo.images.emsNotificationCollector.repoPath -}}
    {{- $name := $productInfo.images.emsNotificationCollector.name -}}
    {{- $tag := $productInfo.images.emsNotificationCollector.tag -}}
    {{- if .Values.global -}}
        {{- if .Values.global.registry -}}
            {{- if .Values.global.registry.url -}}
                {{- $registryUrl = .Values.global.registry.url -}}
            {{- end -}}
        {{- end -}}
    {{- end -}}
    {{- if .Values.imageCredentials -}}
        {{- if .Values.imageCredentials.emsNotificationCollector -}}
            {{- if .Values.imageCredentials.emsNotificationCollector.registry -}}
                {{- if .Values.imageCredentials.emsNotificationCollector.registry.url -}}
                    {{- $registryUrl = .Values.imageCredentials.emsNotificationCollector.registry.url -}}
                {{- end -}}
            {{- end -}}
            {{- if .Values.imageCredentials.emsNotificationCollector.repoPath -}}
                {{- $repoPath = .Values.imageCredentials.emsNotificationCollector.repoPath -}}
            {{- end -}}
        {{- end -}}
    {{- end -}}
    {{- if .Values.images -}}
        {{- if .Values.images.emsNotificationCollector -}}
            {{- if .Values.images.emsNotificationCollector.name -}}
                {{- $name = .Values.images.emsNotificationCollector.name -}}
            {{- end -}}
            {{- if .Values.images.emsNotificationCollector.tag -}}
                {{- $tag = .Values.images.emsNotificationCollector.tag -}}
            {{- end -}}
        {{- end -}}
    {{- end -}}
    {{- if $repoPath -}}
        {{- $repoPath = printf "%s/" $repoPath -}}
    {{- end -}}
    {{- printf "%s/%s%s:%s" $registryUrl $repoPath $name $tag -}}
{{- end -}}

{{/*
The reusedImage path (DR-D1121-067)
*/}}
{{- define "eric-oss-adc-ems-notification-collector.reusedImagePath" }}
    {{- $productInfo := fromYaml (.Files.Get "eric-product-info.yaml") -}}
    {{- $registryUrl := $productInfo.images.pgInitContainer.registry -}}
    {{- $repoPath := $productInfo.images.pgInitContainer.repoPath -}}
    {{- $name := $productInfo.images.pgInitContainer.name -}}
    {{- $tag := $productInfo.images.pgInitContainer.tag -}}
    {{- if .Values.global -}}
        {{- if .Values.global.registry -}}
            {{- if .Values.global.registry.url -}}
                {{- $registryUrl = .Values.global.registry.url -}}
            {{- end -}}
        {{- end -}}
    {{- end -}}
    {{- if .Values.imageCredentials -}}
        {{- if .Values.imageCredentials.pgInitContainer -}}
            {{- if .Values.imageCredentials.pgInitContainer.registry -}}
                {{- if .Values.imageCredentials.pgInitContainer.registry.url -}}
                    {{- $registryUrl = .Values.imageCredentials.pgInitContainer.registry.url -}}
                {{- end -}}
            {{- end -}}
            {{- if .Values.imageCredentials.pgInitContainer.repoPath -}}
                {{- $repoPath = .Values.imageCredentials.pgInitContainer.repoPath -}}
            {{- end -}}
        {{- end -}}
    {{- end -}}
    {{- if .Values.images -}}
        {{- if .Values.images.pgInitContainer -}}
            {{- if .Values.images.pgInitContainer.name -}}
                {{- $name = .Values.images.pgInitContainer.name -}}
            {{- end -}}
            {{- if .Values.images.pgInitContainer.tag -}}
                {{- $tag = .Values.images.pgInitContainer.tag -}}
            {{- end -}}
        {{- end -}}
    {{- end -}}
    {{- if $repoPath -}}
        {{- $repoPath = printf "%s/" $repoPath -}}
    {{- end -}}
    {{- printf "%s/%s%s:%s" $registryUrl $repoPath $name $tag -}}
{{- end -}}

{{/*
Expand the name of the chart.
*/}}
{{- define "eric-oss-adc-ems-notification-collector.name" }}
  {{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create chart version as used by the chart label.
*/}}
{{- define "eric-oss-adc-ems-notification-collector.version" }}
{{- printf "%s" .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "eric-oss-adc-ems-notification-collector.fullname" }}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- $name | trunc 63 | trimSuffix "-" }}
{{/* Ericsson mandates the name defined in metadata should start with chart name. */}}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "eric-oss-adc-ems-notification-collector.chart" }}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a map from ".Values.global" with defaults if missing in values file.
This hides defaults from values file.
*/}}
{{ define "eric-oss-adc-ems-notification-collector.global" }}
  {{- $globalDefaults := dict "security" (dict "tls" (dict "enabled" true)) -}}
  {{- $globalDefaults := merge $globalDefaults (dict "nodeSelector" (dict)) -}}
  {{- $globalDefaults := merge $globalDefaults (dict "internalIPFamily" "") -}}
  {{ if .Values.global }}
    {{- mergeOverwrite $globalDefaults .Values.global | toJson -}}
  {{ else }}
    {{- $globalDefaults | toJson -}}
  {{ end }}
{{ end }}


{{/*
Create image repo path
*/}}
{{- define "eric-oss-adc-ems-notification-collector.repoPath" }}
  {{- if index .Values "imageCredentials" "ems-notification-collector" "repoPath" }}
    {{- index .Values "imageCredentials" "ems-notification-collector" "repoPath" }}
  {{- end }}
{{- end }}

{{/*
Create image registry url
*/}}
{{- define "eric-oss-adc-ems-notification-collector.registryUrl" }}
  {{- $registryURL := "armdocker.rnd.ericsson.se" }}
  {{-  if .Values.global }}
    {{- if .Values.global.registry }}
      {{- if .Values.global.registry.url }}
        {{- $registryURL = .Values.global.registry.url }}
      {{- end }}
    {{- end }}
  {{- end }}
  {{- if index .Values "imageCredentials" "ems-notification-collector" "registry" }}
    {{- if index .Values "imageCredentials" "ems-notification-collector" "registry" "url" }}
      {{- $registryURL = index .Values "imageCredentials" "ems-notification-collector" "registry" "url" }}
    {{- end }}
  {{- end }}
  {{- print $registryURL }}
{{- end -}}

{{/*
Create image pull secrets
*/}}
{{- define "eric-oss-adc-ems-notification-collector.pullSecrets" }}
  {{- $pullSecret := "" }}
  {{- if .Values.global }}
    {{- if .Values.global.pullSecret }}
      {{- $pullSecret = .Values.global.pullSecret }}
    {{- end }}
  {{- end }}
  {{- if index .Values "imageCredentials" }}
    {{- if index .Values "imageCredentials" "pullSecret" }}
      {{- $pullSecret = index .Values "imageCredentials" "pullSecret" }}
    {{- end }}
  {{- end }}
  {{- print $pullSecret }}
{{- end }}

{{/*
DR-D1121-102 main image pull policy
*/}}

{{- define "eric-oss-adc-ems-notification-collector.registryImagePullPolicy" -}}
    {{- $globalRegistryPullPolicy := "IfNotPresent" -}}
    {{- if .Values.global -}}
        {{- if .Values.global.registry -}}
            {{- if .Values.global.registry.imagePullPolicy -}}
                {{- $globalRegistryPullPolicy = .Values.global.registry.imagePullPolicy -}}
            {{- end -}}
        {{- end -}}
    {{- end -}}
    {{- if .Values.imageCredentials.emsNotificationCollector.registry -}}
        {{- if .Values.imageCredentials.emsNotificationCollector.registry.imagePullPolicy -}}
        {{- $globalRegistryPullPolicy = .Values.imageCredentials.emsNotificationCollector.registry.imagePullPolicy -}}
        {{- end -}}
    {{- end -}}
    {{- print $globalRegistryPullPolicy -}}
{{- end -}}

{{/*
DR-D1121-102 reused image pgInitContainer's imagePullPolicy
*/}}
{{- define "eric-oss-adc-ems-notification-collector.pgInitContainer.registryImagePullPolicy" -}}
    {{- $registryImagePullPolicy := "IfNotPresent" -}}
    {{- if .Values.global -}}
        {{- if .Values.global.registry -}}
            {{- if .Values.global.registry.imagePullPolicy -}}
                {{- $registryImagePullPolicy = .Values.global.registry.imagePullPolicy -}}
            {{- end -}}
        {{- end -}}
    {{- end -}}
    {{- if .Values.imageCredentials.pgInitContainer.registry -}}
        {{- if .Values.imageCredentials.pgInitContainer.registry.imagePullPolicy -}}
        {{- $registryImagePullPolicy = .Values.imageCredentials.pgInitContainer.registry.imagePullPolicy -}}
        {{- end -}}
    {{- end -}}
    {{- print $registryImagePullPolicy -}}
{{- end -}}

{{/*
Define timezone
Default: UTC
*/}}
{{- define "eric-oss-adc-ems-notification-collector.timezone" -}}
{{- $timezone := "UTC" -}}
{{- if .Values.global -}}
    {{- if .Values.global.timezone -}}
        {{- $timezone = .Values.global.timezone -}}
    {{- end -}}
{{- end -}}
{{- print $timezone -}}
{{- end -}}

{{/*
Create labels (DR-D1121-068)
*/}}
{{- define "eric-oss-adc-ems-notification-collector.labels" -}}
{{ include "eric-oss-adc-ems-notification-collector.selectorLabels" . }}
app.kubernetes.io/version: {{ template "eric-oss-adc-ems-notification-collector.version" . }}
chart: {{ template "eric-oss-adc-ems-notification-collector.chart" . }}
component: query
heritage: {{ .Release.Service }}
release: {{ .Release.Name }}
{{- if .Values.labels }}
{{ toYaml .Values.labels }}
{{- end -}}
{{- end -}}

{{/*
Return the fsgroup set via global parameter if it's set, otherwise 10000
*/}}
{{- define "eric-oss-adc-ems-notification-collector.fsGroup.coordinated" -}}
  {{- if .Values.global -}}
    {{- if .Values.global.fsGroup -}}
      {{- if .Values.global.fsGroup.manual -}}
        {{ .Values.global.fsGroup.manual }}
      {{- else -}}
        {{- if eq .Values.global.fsGroup.namespace true -}}
          # The 'default' defined in the Security Policy will be used.
        {{- else -}}
          10000
      {{- end -}}
    {{- end -}}
  {{- else -}}
    10000
  {{- end -}}
  {{- else -}}
    10000
  {{- end -}}
{{- end -}}

{{/*
Selector labels
*/}}
{{- define "eric-oss-adc-ems-notification-collector.selectorLabels" -}}
app.kubernetes.io/name: {{ include "eric-oss-adc-ems-notification-collector.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "eric-oss-adc-ems-notification-collector.serviceAccountName" -}}
  {{- if .Values.serviceAccount.create }}
    {{- default (include "eric-oss-adc-ems-notification-collector.fullname" .) .Values.serviceAccount.name }}
  {{- else }}
    {{- default "default" .Values.serviceAccount.name }}
  {{- end }}
{{- end }}

{{/*
Create a user defined annotation (DR-D1121-065)
*/}}
{{ define "eric-oss-adc-ems-notification-collector.config-annotations" }}
{{- if .Values.annotations -}}
{{- range $name, $config := .Values.annotations }}
{{ $name }}: {{ tpl $config $ }}
{{- end }}
{{- end }}
{{- end}}

{{- define "eric-oss-adc-ems-notification-collector.product-info" }}
ericsson.com/product-name: {{ (fromYaml (.Files.Get "eric-product-info.yaml")).productName | quote }}
ericsson.com/product-number: {{ (fromYaml (.Files.Get "eric-product-info.yaml")).productNumber | quote }}
ericsson.com/product-revision: {{regexReplaceAll "(.*)[+|-].*" .Chart.Version "${1}" | quote }}
{{- end }}

{{/*
Define the role reference for security policy
*/}}
{{- define "eric-oss-adc-ems-notification-collector.securityPolicy.reference" -}}
  {{- if .Values.global -}}
    {{- if .Values.global.security -}}
      {{- if .Values.global.security.policyReferenceMap -}}
        {{ $mapped := index .Values "global" "security" "policyReferenceMap" "default-restricted-security-policy" }}
        {{- if $mapped -}}
          {{ $mapped }}
        {{- else -}}
          default-restricted-security-policy
        {{- end -}}
      {{- else -}}
        default-restricted-security-policy
      {{- end -}}
    {{- else -}}
      default-restricted-security-policy
    {{- end -}}
  {{- else -}}
    default-restricted-security-policy
  {{- end -}}
{{- end -}}

{{/*
Define the annotations for security policy
*/}}
{{- define "eric-oss-adc-ems-notification-collector.securityPolicy.annotations" -}}
# Automatically generated annotations for documentation purposes.
{{- end -}}

{{/*
Define Pod Disruption Budget value taking into account its type (int or string)
*/}}
{{- define "eric-oss-adc-ems-notification-collector.pod-disruption-budget" -}}
  {{- if kindIs "string" .Values.podDisruptionBudget.minAvailable -}}
    {{- print .Values.podDisruptionBudget.minAvailable | quote -}}
  {{- else -}}
    {{- print .Values.podDisruptionBudget.minAvailable | atoi -}}
  {{- end -}}
{{- end -}}

{{/*
Create a merged set of nodeSelectors from global and service level.
*/}}
{{ define "eric-oss-adc-ems-notification-collector.nodeSelector" }}
  {{- $g := fromJson (include "eric-oss-adc-ems-notification-collector.global" .) -}}
  {{- if .Values.nodeSelector -}}
    {{- range $key, $localValue := .Values.nodeSelector -}}
      {{- if hasKey $g.nodeSelector $key -}}
          {{- $globalValue := index $g.nodeSelector $key -}}
          {{- if ne $globalValue $localValue -}}
            {{- printf "nodeSelector \"%s\" is specified in both global (%s: %s) and service level (%s: %s) with differing values which is not allowed." $key $key $globalValue $key $localValue | fail -}}
          {{- end -}}
      {{- end -}}
    {{- end -}}
    {{- toYaml (merge $g.nodeSelector .Values.nodeSelector) | trim -}}
  {{- else -}}
    {{- toYaml $g.nodeSelector | trim -}}
  {{- end -}}
{{ end }}

{{/*
Define podAntiAffinity
*/}}
{{- define "eric-oss-adc-ems-notification-collector.podAntiAffinity" -}}
{{- if eq .Values.affinity.podAntiAffinity "hard" -}}
requiredDuringSchedulingIgnoredDuringExecution:
- labelSelector:
    matchExpressions:
    - key: app
      operator: In
      values:
      - {{ template "eric-oss-adc-ems-notification-collector.name" . }}
  topologyKey: "kubernetes.io/hostname"
{{- else if eq .Values.affinity.podAntiAffinity "soft" -}}
preferredDuringSchedulingIgnoredDuringExecution:
- weight: 100
  podAffinityTerm:
    labelSelector:
      matchExpressions:
      - key: app
        operator: In
        values:
        - {{ template "eric-oss-adc-ems-notification-collector.name" . }}
    topologyKey: "kubernetes.io/hostname"
{{- else -}}
{{ fail "A valid .Values.affinity.podAntiAffinity entry required!" }}
{{- end -}}
{{- end -}}

# API contracts

Requested API mode: **${{ values.apiType }}**.

{% if values.apiType == 'rest' or values.apiType == 'both' %}
REST is planned. This skeleton exposes only Actuator health. Implement domain endpoints
and export Springdoc to openapi.yaml before registering an API entity.
{% else %}
REST: Not applicable yet. No domain REST endpoints are requested.
{% endif %}
{% if values.apiType == 'events' or values.apiType == 'both' %}
Events are planned. Choose a registered broker, define real messages and AsyncAPI
before adding producer/consumer code and an API entity. No fake event is generated.
{% else %}
AsyncAPI: Not applicable yet. No broker or messages are used.
{% endif %}

# Runbook

Service: ${{ values.name }}. ${{ values.description }}

Check /actuator/health, container logs and image revision. Roll back the image if needed; never delete persistent data as a recovery shortcut.

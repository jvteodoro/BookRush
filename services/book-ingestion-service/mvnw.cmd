@echo off
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  mvn %*
  exit /b %ERRORLEVEL%
)
where docker >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  docker run --rm -v "%CD%:/workspace" -w /workspace maven:3.9-eclipse-temurin-21 mvn %*
  exit /b %ERRORLEVEL%
)
echo Maven local nao esta instalado e Docker nao esta disponivel; use o Dockerfile do servico.
exit /b 127

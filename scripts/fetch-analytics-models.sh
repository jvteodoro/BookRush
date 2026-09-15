#!/usr/bin/env bash
set -Eeuo pipefail
# Explicit model preparation. No model is downloaded by application startup.
cache_dir="${ANALYTICS_MODEL_CACHE_DIR:-${PWD}/.analytics-model-cache}"
mkdir -p "$cache_dir"
cat >&2 <<MSG
Model cache: $cache_dir
This command only prepares the directory and verifies configured files. Obtain
artifacts through the approved supply chain, then set MODEL_*_SHA256 variables.
MSG
verify() { local name="$1" file="$2" expected="${3:-}"; [[ -f "$cache_dir/$file" ]] || { echo "missing $name: $cache_dir/$file" >&2; return 1; }; [[ -z "$expected" ]] || echo "$expected  $cache_dir/$file" | sha256sum -c -; }
verify fastText "lid.176.bin" "${FASTTEXT_LID_SHA256:-}"
verify spacy-en "en_core_web_sm-3.8.0.tar.gz" "${SPACY_EN_SHA256:-}"
verify spacy-pt "pt_core_news_sm.tar.gz" "${SPACY_PT_SHA256:-}"
verify bge-m3 "bge-m3.safetensors" "${BGE_M3_SHA256:-}"
verify mdeberta-nli "mdeberta-nli.safetensors" "${MDEBERTA_NLI_SHA256:-}"
echo 'Analytics model artifacts verified.'

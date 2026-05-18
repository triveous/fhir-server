#!/usr/bin/env bash
set -uo pipefail

# Reproducer (no auth): seeds resources in DEFAULT, then reads them via a
# tenant URL. Validates that the SystemAwareRequestTenantPartitionInterceptor
# correctly widens reads of:
#   - HAPI built-in non-partitionable types (e.g. Questionnaire)
#   - configured default-only types (e.g. Composition)
# to the DEFAULT partition.

FHIR_PORT="${FHIR_PORT:-8090}"
FHIR_BASE="http://localhost:${FHIR_PORT}/fhir"
TENANT_A="TENANT-A"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
WAR="${REPO_ROOT}/target/ROOT.war"
RESP_FILE="/tmp/fhir-repro-resp.json"
FHIR_LOG="/tmp/fhir-repro.log"
FHIR_PID=""

PASS_COUNT=0
FAIL_COUNT=0

pass() { echo "  PASS: $1"; PASS_COUNT=$((PASS_COUNT + 1)); }
fail() { echo "  FAIL: $1 — $2"; FAIL_COUNT=$((FAIL_COUNT + 1)); }

http() {
  local method="$1" url="$2" body="${3:-}"
  local args=(-s -o "${RESP_FILE}" -w "%{http_code}" -X "${method}")
  [[ -n "${body}" ]] && args+=(-H "Content-Type: application/fhir+json" -d "${body}")
  curl "${args[@]}" "${url}"
}

wait_for_url() {
  local url="$1" max="${2:-180}"
  local elapsed=0
  until [[ "$(curl -s -o /dev/null -w "%{http_code}" "${url}" 2>/dev/null)" == "200" ]]; do
    if (( elapsed >= max )); then
      echo "  ERROR: ${url} not ready in ${max}s. Tail of log:"
      tail -50 "${FHIR_LOG}"
      exit 1
    fi
    sleep 2
    ((elapsed += 2))
  done
}

cleanup() {
  if [[ -n "${FHIR_PID}" ]] && kill -0 "${FHIR_PID}" 2>/dev/null; then
    kill "${FHIR_PID}" 2>/dev/null || true
    wait "${FHIR_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

[[ -f "${WAR}" ]] || { echo "missing ${WAR}; build with: mvn clean package -DskipTests"; exit 1; }

echo "Starting FHIR server (auth disabled, partitioning enabled)..."
java -jar "${WAR}" \
  --server.port="${FHIR_PORT}" \
  "--spring.datasource.url=jdbc:h2:mem:dbr4-repro;DB_CLOSE_DELAY=-1" \
  --hapi.fhir.fhir_version=r4 \
  --hapi.fhir.cr_enabled=false \
  --hapi.fhir.partitioning.partitioning_include_in_search_hashes=false \
  "--hapi.fhir.partitioning.default_only_resource_types[0]=Composition" \
  --hapi.fhir.security.inbound.authentication.enabled=false \
  --hapi.fhir.security.inbound.authorization.enabled=false \
  >"${FHIR_LOG}" 2>&1 &
FHIR_PID=$!

wait_for_url "${FHIR_BASE}/metadata" 180
echo "Server up."

echo "Creating partition ${TENANT_A}..."
http POST "${FHIR_BASE}/DEFAULT/\$partition-management-create-partition" \
  '{"resourceType":"Parameters","parameter":[{"name":"id","valueInteger":1},{"name":"name","valueCode":"TENANT-A"}]}' > /dev/null

echo ""
echo "Running tests..."

# --- test 1: Composition (in default_only_resource_types) is widened on read.
COMP_IDENT="repro-composition-$$-${RANDOM}"
COMP_BODY='{"resourceType":"Composition","status":"final","identifier":{"system":"urn:test","value":"'"${COMP_IDENT}"'"},"type":{"text":"t"},"date":"2024-01-01","author":[{"display":"x"}],"title":"t"}'
STATUS=$(http POST "${FHIR_BASE}/DEFAULT/Composition" "${COMP_BODY}")
if [[ "${STATUS}" != "201" ]]; then
  fail "composition_read_via_tenant_url_returns_default_data" "seed POST returned ${STATUS}"
else
  CREATED_ID=$(jq -r '.id' "${RESP_FILE}")
  STATUS=$(http GET "${FHIR_BASE}/${TENANT_A}/Composition?identifier=${COMP_IDENT}")
  TOTAL=$(jq -r '.total // 0' "${RESP_FILE}")
  FOUND_ID=$(jq -r '.entry[0].resource.id // ""' "${RESP_FILE}")
  if [[ "${STATUS}" == "200" && "${TOTAL}" == "1" && "${FOUND_ID}" == "${CREATED_ID}" ]]; then
    pass "composition_read_via_tenant_url_returns_default_data"
  else
    fail "composition_read_via_tenant_url_returns_default_data" \
      "status=${STATUS} total=${TOTAL} found_id=${FOUND_ID} expected=${CREATED_ID}"
  fi
fi

# --- test 2: Questionnaire (HAPI built-in non-partitionable) is widened on read.
Q_IDENT="repro-questionnaire-$$-${RANDOM}"
Q_BODY='{"resourceType":"Questionnaire","status":"draft","identifier":[{"system":"urn:test","value":"'"${Q_IDENT}"'"}]}'
STATUS=$(http POST "${FHIR_BASE}/DEFAULT/Questionnaire" "${Q_BODY}")
if [[ "${STATUS}" != "201" ]]; then
  fail "questionnaire_read_via_tenant_url_returns_default_data" "seed POST returned ${STATUS}"
else
  CREATED_ID=$(jq -r '.id' "${RESP_FILE}")
  STATUS=$(http GET "${FHIR_BASE}/${TENANT_A}/Questionnaire?identifier=${Q_IDENT}")
  TOTAL=$(jq -r '.total // 0' "${RESP_FILE}")
  FOUND_ID=$(jq -r '.entry[0].resource.id // ""' "${RESP_FILE}")
  if [[ "${STATUS}" == "200" && "${TOTAL}" == "1" && "${FOUND_ID}" == "${CREATED_ID}" ]]; then
    pass "questionnaire_read_via_tenant_url_returns_default_data"
  else
    fail "questionnaire_read_via_tenant_url_returns_default_data" \
      "status=${STATUS} total=${TOTAL} found_id=${FOUND_ID} expected=${CREATED_ID}"
  fi
fi

# --- test 3: Patient (regular partitionable) stays tenant-isolated.
# Sanity check that we didn't break normal tenant isolation.
PAT_BODY='{"resourceType":"Patient","name":[{"family":"DefaultOnly"}]}'
STATUS=$(http POST "${FHIR_BASE}/DEFAULT/Patient" "${PAT_BODY}")
if [[ "${STATUS}" != "201" ]]; then
  fail "patient_in_default_not_visible_in_tenant" "seed POST returned ${STATUS}"
else
  CREATED_ID=$(jq -r '.id' "${RESP_FILE}")
  STATUS=$(http GET "${FHIR_BASE}/${TENANT_A}/Patient/${CREATED_ID}")
  if [[ "${STATUS}" == "404" || "${STATUS}" == "410" ]]; then
    pass "patient_in_default_not_visible_in_tenant"
  else
    fail "patient_in_default_not_visible_in_tenant" \
      "expected 404/410 from /TENANT-A/Patient/${CREATED_ID}, got ${STATUS}"
  fi
fi

echo ""
echo "Results: ${PASS_COUNT} passed, ${FAIL_COUNT} failed"
[[ "${FAIL_COUNT}" -eq 0 ]]

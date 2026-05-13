#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
KEYCLOAK_IMAGE="quay.io/keycloak/keycloak:24.0.3"
KEYCLOAK_CONTAINER="keycloak-test-mt"
KEYCLOAK_PORT="${KEYCLOAK_PORT:-8180}"
FHIR_PORT="${FHIR_PORT:-8090}"
REALM="test"
CLIENT_ID="fhir-client"
TENANT_A="TENANT-A"
TENANT_B="TENANT-B"
FHIR_BASE="http://localhost:${FHIR_PORT}/fhir"
KEYCLOAK_BASE="http://localhost:${KEYCLOAK_PORT}"
TOKEN_URL="${KEYCLOAK_BASE}/realms/${REALM}/protocol/openid-connect/token"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
MVN="${MVN:-$(command -v mvn 2>/dev/null || echo "${HOME}/.m2/wrapper/dists/apache-maven-3.8.4-bin/66e9f8f4/apache-maven-3.8.4/bin/mvn")}"
FHIR_LOG="/tmp/fhir-server-mt.log"
RESP_FILE="/tmp/fhir-mt-resp.json"
FHIR_PID=""

PASS_COUNT=0
FAIL_COUNT=0

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
pass() { echo "  PASS: $1"; PASS_COUNT=$((PASS_COUNT + 1)); }

fail() {
  echo "  FAIL: $1 — $2"
  FAIL_COUNT=$((FAIL_COUNT + 1))
}

get_token() {
  local username="$1" password="$2"
  curl -s -X POST "${TOKEN_URL}" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&client_id=${CLIENT_ID}&username=${username}&password=${password}" \
    | jq -r '.access_token'
}

# Polls $1 until it returns HTTP 200, or times out after $2 seconds.
wait_for_url() {
  local url="$1" max="${2:-120}"
  local elapsed=0
  echo "  Waiting for ${url} ..."
  until [[ "$(curl -s -o /dev/null -w "%{http_code}" "${url}" 2>/dev/null)" != "000" ]]; do
    if (( elapsed >= max )); then
      echo "  ERROR: ${url} did not become ready within ${max}s"
      exit 1
    fi
    sleep 2
    ((elapsed += 2))
  done
}

# Returns the HTTP status code; writes body to $RESP_FILE.
http() {
  local method="$1" url="$2" token="${3:-}" body="${4:-}"
  local args=(-s -o "${RESP_FILE}" -w "%{http_code}" -X "${method}")
  [[ -n "${token}" ]] && args+=(-H "Authorization: Bearer ${token}")
  [[ -n "${body}" ]] && args+=(-H "Content-Type: application/fhir+json" -d "${body}")
  curl "${args[@]}" "${url}"
}

cleanup() {
  echo ""
  echo "Cleaning up..."
  if [[ -n "${FHIR_PID}" ]] && kill -0 "${FHIR_PID}" 2>/dev/null; then
    kill "${FHIR_PID}" 2>/dev/null || true
    wait "${FHIR_PID}" 2>/dev/null || true
  fi
  docker stop "${KEYCLOAK_CONTAINER}" 2>/dev/null || true
  docker rm  "${KEYCLOAK_CONTAINER}" 2>/dev/null || true
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# Optional build
# ---------------------------------------------------------------------------
SKIP_BUILD=false
for arg in "$@"; do [[ "$arg" == "--skip-build" ]] && SKIP_BUILD=true; done

if [[ "${SKIP_BUILD}" == false ]]; then
  echo "Building..."
  (cd "${REPO_ROOT}" && "${MVN}" clean package -DskipTests -q)
fi

WAR="${REPO_ROOT}/target/ROOT.war"
if [[ ! -f "${WAR}" ]]; then
  echo "ERROR: ${WAR} not found. Run without --skip-build or build manually."
  exit 1
fi

# ---------------------------------------------------------------------------
# Start Keycloak
# ---------------------------------------------------------------------------
echo "Starting Keycloak..."
docker rm -f "${KEYCLOAK_CONTAINER}" 2>/dev/null || true
docker run -d \
  --name "${KEYCLOAK_CONTAINER}" \
  -p "${KEYCLOAK_PORT}:8080" \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  -v "${REPO_ROOT}/src/test/resources/keycloak/test-realm.json:/opt/keycloak/data/import/test-realm.json" \
  "${KEYCLOAK_IMAGE}" start-dev --import-realm > /dev/null

wait_for_url "${KEYCLOAK_BASE}/realms/${REALM}" 120

# ---------------------------------------------------------------------------
# Start FHIR server
# ---------------------------------------------------------------------------
echo "Starting FHIR server..."
java -jar "${WAR}" \
  --server.port="${FHIR_PORT}" \
  "--spring.datasource.url=jdbc:h2:mem:dbr4-jwt-mt;DB_CLOSE_DELAY=-1" \
  --hapi.fhir.fhir_version=r4 \
  --hapi.fhir.cr_enabled=false \
  --hapi.fhir.partitioning.partitioning_include_in_search_hashes=false \
  --hapi.fhir.security.inbound.authentication.enabled=true \
  --hapi.fhir.security.inbound.authentication.proceed-to-authorization-on-no-auth=false \
  --hapi.fhir.security.inbound.authentication.proceed-to-authorization-on-failure=false \
  "--hapi.fhir.security.inbound.authentication.allowed-issuer-patterns[0]=http://localhost:${KEYCLOAK_PORT}/realms/${REALM}" \
  >"${FHIR_LOG}" 2>&1 &
FHIR_PID=$!

wait_for_url "${FHIR_BASE}/metadata" 180

# ---------------------------------------------------------------------------
# Create partitions (admin setup)
# ---------------------------------------------------------------------------
echo "Creating partitions..."
ADMIN_TOKEN="$(get_token admin password)"

create_partition() {
  local id="$1" name="$2"
  local body
  body=$(printf '{"resourceType":"Parameters","parameter":[{"name":"id","valueInteger":%d},{"name":"name","valueCode":"%s"}]}' "${id}" "${name}")
  http POST "${FHIR_BASE}/DEFAULT/\$partition-management-create-partition" "${ADMIN_TOKEN}" "${body}" > /dev/null
}

create_partition 1 "${TENANT_A}"
create_partition 2 "${TENANT_B}"

# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------
echo ""
echo "Running tests..."

# ------ test 1: Tenant-A user can create and read in Tenant-A ---------------
TOKEN_A="$(get_token user-a password)"
STATUS=$(http POST "${FHIR_BASE}/${TENANT_A}/Patient" "${TOKEN_A}" \
  '{"resourceType":"Patient","name":[{"family":"TenantAFamily"}]}')
if [[ "${STATUS}" == "201" ]]; then
  PATIENT_ID=$(jq -r '.id' "${RESP_FILE}")
  STATUS2=$(http GET "${FHIR_BASE}/${TENANT_A}/Patient/${PATIENT_ID}" "${TOKEN_A}")
  FAMILY=$(jq -r '.name[0].family' "${RESP_FILE}")
  if [[ "${STATUS2}" == "200" && "${FAMILY}" == "TenantAFamily" ]]; then
    pass "tenant_a_user_can_create_and_read_in_tenant_a"
  else
    fail "tenant_a_user_can_create_and_read_in_tenant_a" "GET returned status=${STATUS2} family=${FAMILY}"
  fi
else
  fail "tenant_a_user_can_create_and_read_in_tenant_a" "POST returned status=${STATUS}"
fi

# ------ test 2: Tenant-B user can create and read in Tenant-B ---------------
TOKEN_B="$(get_token user-b password)"
STATUS=$(http POST "${FHIR_BASE}/${TENANT_B}/Patient" "${TOKEN_B}" \
  '{"resourceType":"Patient","name":[{"family":"TenantBFamily"}]}')
if [[ "${STATUS}" == "201" ]]; then
  PATIENT_ID=$(jq -r '.id' "${RESP_FILE}")
  STATUS2=$(http GET "${FHIR_BASE}/${TENANT_B}/Patient/${PATIENT_ID}" "${TOKEN_B}")
  FAMILY=$(jq -r '.name[0].family' "${RESP_FILE}")
  if [[ "${STATUS2}" == "200" && "${FAMILY}" == "TenantBFamily" ]]; then
    pass "tenant_b_user_can_create_and_read_in_tenant_b"
  else
    fail "tenant_b_user_can_create_and_read_in_tenant_b" "GET returned status=${STATUS2} family=${FAMILY}"
  fi
else
  fail "tenant_b_user_can_create_and_read_in_tenant_b" "POST returned status=${STATUS}"
fi

# ------ test 3: Tenant-A user is blocked from Tenant-B ---------------------
STATUS=$(http GET "${FHIR_BASE}/${TENANT_B}/Patient" "${TOKEN_A}")
if [[ "${STATUS}" == "403" ]]; then
  pass "tenant_a_user_blocked_from_tenant_b"
else
  fail "tenant_a_user_blocked_from_tenant_b" "expected 403, got ${STATUS}"
fi

# ------ test 4: Tenant-B user is blocked from Tenant-A ---------------------
STATUS=$(http GET "${FHIR_BASE}/${TENANT_A}/Patient" "${TOKEN_B}")
if [[ "${STATUS}" == "403" ]]; then
  pass "tenant_b_user_blocked_from_tenant_a"
else
  fail "tenant_b_user_blocked_from_tenant_a" "expected 403, got ${STATUS}"
fi

# ------ test 5: Tenants have isolated data ----------------------------------
http POST "${FHIR_BASE}/${TENANT_A}/Patient" "${TOKEN_A}" \
  '{"resourceType":"Patient","name":[{"family":"IsolatedA"}]}' > /dev/null
http POST "${FHIR_BASE}/${TENANT_B}/Patient" "${TOKEN_B}" \
  '{"resourceType":"Patient","name":[{"family":"IsolatedB"}]}' > /dev/null

http GET "${FHIR_BASE}/${TENANT_A}/Patient?_count=100" "${TOKEN_A}" > /dev/null
FAMILIES_A=$(jq -r '[.entry[]?.resource.name[]?.family] | .[]' "${RESP_FILE}" 2>/dev/null || true)

http GET "${FHIR_BASE}/${TENANT_B}/Patient?_count=100" "${TOKEN_B}" > /dev/null
FAMILIES_B=$(jq -r '[.entry[]?.resource.name[]?.family] | .[]' "${RESP_FILE}" 2>/dev/null || true)

ISOLATED_OK=true
if echo "${FAMILIES_A}" | grep -q "IsolatedB"; then
  fail "tenants_have_isolated_data" "TENANT-A should not see IsolatedB"
  ISOLATED_OK=false
fi
if echo "${FAMILIES_B}" | grep -q "IsolatedA"; then
  fail "tenants_have_isolated_data" "TENANT-B should not see IsolatedA"
  ISOLATED_OK=false
fi
if ! echo "${FAMILIES_A}" | grep -q "IsolatedA"; then
  fail "tenants_have_isolated_data" "TENANT-A should see IsolatedA"
  ISOLATED_OK=false
fi
if ! echo "${FAMILIES_B}" | grep -q "IsolatedB"; then
  fail "tenants_have_isolated_data" "TENANT-B should see IsolatedB"
  ISOLATED_OK=false
fi
if [[ "${ISOLATED_OK}" == true ]]; then
  pass "tenants_have_isolated_data"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "Results: ${PASS_COUNT} passed, ${FAIL_COUNT} failed"
[[ "${FAIL_COUNT}" -eq 0 ]]

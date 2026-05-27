package ca.uhn.fhir.jpa.starter.authnz.inbound.authorization;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.partition.IRequestPartitionHelperSvc;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.server.interceptor.partition.RequestTenantPartitionInterceptor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

// Stock RequestTenantPartitionInterceptor throws when a request has no tenant.
// Server-internal work (e.g. SearchParameter registry refresh on startup)
// issues SystemRequestDetails without a URL tenant. Route those to the
// DEFAULT partition — that's where server-wide HAPI artifacts (custom
// SearchParameters, StructureDefinitions) live. allPartitions() is rejected
// at runtime by HAPI-1220 when allowReferencesAcrossPartitions is NOT_ALLOWED.
//
// On reads, the stock interceptor also routes everything to the URL tenant.
// That breaks definitional/non-partitionable resources (StructureDefinition,
// SearchParameter, ValueSet, CodeSystem, Questionnaire, …) which HAPI-1318
// forces to live in DEFAULT only — a GET at /fhir/<tenant>/<Type> for those
// would scope to the tenant partition and always return empty. We widen
// non-partitionable reads to DEFAULT using HAPI's authoritative list via
// IRequestPartitionHelperSvc#isResourcePartitionable.
//
// We additionally accept a configurable set of "default-only" resource types
// (see hapi.fhir.partitioning.default-only-resource-types in application.yaml).
// Resources of those types are also redirected to DEFAULT on read even when
// requested from a per-tenant URL — this covers shared "config" resources
// (Composition, StructureMap, …) that HAPI's built-in non-partitionable list
// does not cover, and which our app fetches via tenant-scoped URLs before
// login. WRITE-side interception is deliberately NOT added: writes against
// /fhir/<tenant>/<Type> for these types stay tenant-scoped so a misconfigured
// client cannot silently mutate the shared default-partition config.
//
// Implementation note: all partition routing flows through the
// STORAGE_PARTITION_IDENTIFY_ANY pointcut (registered by the parent class) →
// extractPartitionIdFromRequest below. The STORAGE_PARTITION_IDENTIFY_READ
// pointcut is unreachable as long as _ANY is registered (see
// BaseRequestPartitionHelperSvc#determineReadPartitionForRequest — _READ is
// in an `else if` branch only entered when _ANY has no hooks).
//
// Fan-out operations (`<ResourceType>/<id>/$everything`, MDM subscription
// loader, and any HAPI internal that issues a sub-search via the DAO
// `search(SearchParameterMap)` overload — see HAPI 7.2.0
// JpaResourceDaoEncounter#encounterInstanceEverything which ignores its
// HttpServletRequest parameter and calls `search(paramMap)` with no
// RequestDetails) lose the URL tenant on their internal sub-reads. The
// _ANY hook then fires for those sub-reads with a RequestDetails that has
// no tenant set, and super.extractPartitionIdFromRequest throws — surfacing
// to callers as the misleading
// "HAPI-1319: No interceptor provided a value for pointcut: STORAGE_PARTITION_IDENTIFY_READ".
//
// Fix: capture the outer request's tenant in a per-thread holder at
// SERVER_INCOMING_REQUEST_PRE_HANDLED, and use it as the fallback inside
// extractPartitionIdFromRequest when the sub-request RequestDetails carries
// no tenant of its own. Cleared on SERVER_OUTGOING_RESPONSE /
// SERVER_PROCESSING_COMPLETED so it can't leak across requests. The outer
// URL is still the source of truth — sub-reads just inherit it.
public class SystemAwareRequestTenantPartitionInterceptor extends RequestTenantPartitionInterceptor {

	private static final ThreadLocal<String> OUTER_REQUEST_TENANT = new ThreadLocal<>();

	private final IRequestPartitionHelperSvc myPartitionHelperSvc;
	private final Set<String> myAdditionalDefaultOnlyTypes;

	public SystemAwareRequestTenantPartitionInterceptor(IRequestPartitionHelperSvc thePartitionHelperSvc) {
		this(thePartitionHelperSvc, Collections.emptySet());
	}

	public SystemAwareRequestTenantPartitionInterceptor(
			IRequestPartitionHelperSvc thePartitionHelperSvc,
			Collection<String> theAdditionalDefaultOnlyTypes) {
		this.myPartitionHelperSvc = thePartitionHelperSvc;
		Set<String> lowered = new HashSet<>();
		if (theAdditionalDefaultOnlyTypes != null) {
			for (String t : theAdditionalDefaultOnlyTypes) {
				if (t != null && !t.isEmpty()) {
					lowered.add(t.toLowerCase(Locale.ROOT));
				}
			}
		}
		this.myAdditionalDefaultOnlyTypes = Collections.unmodifiableSet(lowered);
	}

	@Override
	protected RequestPartitionId extractPartitionIdFromRequest(RequestDetails theRequestDetails) {
		// TODO(diagnostic): remove once $everything is verified working
		String outerHolder = OUTER_REQUEST_TENANT.get();
		String detailsType = theRequestDetails == null ? "null" : theRequestDetails.getClass().getSimpleName();
		String detailsTenant = theRequestDetails == null ? "<null>" : String.valueOf(theRequestDetails.getTenantId());
		System.err.println("[partition-debug] extractPartitionIdFromRequest: requestDetails=" + detailsType
				+ " tenantId=" + detailsTenant + " threadLocalOuter=" + outerHolder
				+ " thread=" + Thread.currentThread().getName());

		// HAPI's BaseHapiFhirResourceDao#search(SearchParameterMap) overload
		// calls #search(map, null), so theRequestDetails can be null on the
		// fan-out sub-read code path (e.g. inside Encounter/<id>/$everything).
		// Try the outer-request tenant holder first in that case.
		if (theRequestDetails == null) {
			String outer = OUTER_REQUEST_TENANT.get();
			if (outer != null && !outer.isEmpty()) {
				return RequestPartitionId.fromPartitionName(outer);
			}
			// No outer tenant captured (e.g. server-internal startup work);
			// fall back to DEFAULT rather than throwing.
			return RequestPartitionId.defaultPartition();
		}
		if (theRequestDetails instanceof SystemRequestDetails) {
			// If we're inside a fan-out sub-read of a tenant-scoped outer
			// request (e.g. Encounter/<id>/$everything served by HAPI's
			// JpaResourceDaoEncounter which constructs a fresh
			// SystemRequestDetails with no explicit partition), inherit the
			// outer tenant rather than dropping to DEFAULT — otherwise the
			// sub-read would query the wrong partition.
			String outer = OUTER_REQUEST_TENANT.get();
			if (outer != null && !outer.isEmpty()) {
				System.err.println("[partition-debug] SystemRequestDetails → using outer tenant " + outer);
				return RequestPartitionId.fromPartitionName(outer);
			}
			return RequestPartitionId.defaultPartition();
		}
		// Widening only applies to reads — writes against /fhir/<tenant>/<Type>
		// must stay tenant-scoped so a misconfigured client cannot silently
		// mutate shared default-partition config.
		if (theRequestDetails.getRequestType() == RequestTypeEnum.GET) {
			String resourceType = theRequestDetails.getResourceName();
			if (resourceType != null
					&& (!myPartitionHelperSvc.isResourcePartitionable(resourceType)
							|| myAdditionalDefaultOnlyTypes.contains(resourceType.toLowerCase(Locale.ROOT)))) {
				return RequestPartitionId.defaultPartition();
			}
		}
		// Same fallback for non-null sub-request RequestDetails that simply
		// lack a tenant of their own.
		String tenantId = theRequestDetails.getTenantId();
		if (tenantId == null || tenantId.isEmpty()) {
			String outer = OUTER_REQUEST_TENANT.get();
			if (outer != null && !outer.isEmpty()) {
				return RequestPartitionId.fromPartitionName(outer);
			}
		}
		return super.extractPartitionIdFromRequest(theRequestDetails);
	}

	// Capture the outer (user-facing) request's tenant so internal sub-reads
	// fired from fan-out operations can inherit it. Only stash if the URL
	// actually carries a tenant — SystemRequestDetails and other internal
	// requests already route via the override above and shouldn't pollute the
	// holder for the rest of the thread.
	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
	public void captureOuterRequestTenant(RequestDetails theRequestDetails) {
		String tenantId = theRequestDetails == null ? null : theRequestDetails.getTenantId();
		System.err.println("[partition-debug] PRE_HANDLED hook fired: tenantId=" + tenantId
				+ " isSystem=" + (theRequestDetails instanceof SystemRequestDetails)
				+ " thread=" + Thread.currentThread().getName());
		if (theRequestDetails instanceof SystemRequestDetails) {
			return;
		}
		if (tenantId != null && !tenantId.isEmpty()) {
			OUTER_REQUEST_TENANT.set(tenantId);
		}
	}

	// Clear at the end of each top-level request — fires for both success and
	// failure paths per HAPI's contract — so a Tomcat worker thread reused for
	// a different tenant's request cannot inherit the previous tenant's holder
	// value.
	@Hook(Pointcut.SERVER_PROCESSING_COMPLETED)
	public void clearOuterRequestTenant(RequestDetails theRequestDetails) {
		System.err.println("[partition-debug] COMPLETED hook fired thread=" + Thread.currentThread().getName());
		OUTER_REQUEST_TENANT.remove();
	}
}

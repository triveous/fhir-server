package ca.uhn.fhir.jpa.starter.authnz.inbound.authorization;

import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.model.util.JpaConstants;
import ca.uhn.fhir.jpa.partition.IRequestPartitionHelperSvc;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.server.interceptor.partition.RequestTenantPartitionInterceptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
// Implementation note: HAPI's BaseRequestPartitionHelperSvc#determineReadPartitionForRequest
// fires STORAGE_PARTITION_IDENTIFY_ANY first and only falls back to
// STORAGE_PARTITION_IDENTIFY_READ when no _ANY hook is registered. Because the
// parent class already registers an _ANY hook (partitionIdentifyCreate, which
// delegates to extractPartitionIdFromRequest), the _READ pointcut never fires
// here. All routing logic therefore lives in the extractPartitionIdFromRequest
// override below.
public class SystemAwareRequestTenantPartitionInterceptor extends RequestTenantPartitionInterceptor {

	private final IRequestPartitionHelperSvc myPartitionHelperSvc;
	private final Set<String> myAdditionalDefaultOnlyTypes;
	// Partitionable types whose GET reads should span the tenant partition AND
	// DEFAULT, so a tenant sees both its own instances and the shared
	// default-partition ones in a single result set. See mergeTenantWithDefault.
	private final Set<String> myMergeWithDefaultTypes;

	public SystemAwareRequestTenantPartitionInterceptor(IRequestPartitionHelperSvc thePartitionHelperSvc) {
		this(thePartitionHelperSvc, Collections.emptySet(), Collections.emptySet());
	}

	public SystemAwareRequestTenantPartitionInterceptor(
			IRequestPartitionHelperSvc thePartitionHelperSvc,
			Collection<String> theAdditionalDefaultOnlyTypes) {
		this(thePartitionHelperSvc, theAdditionalDefaultOnlyTypes, Collections.emptySet());
	}

	public SystemAwareRequestTenantPartitionInterceptor(
			IRequestPartitionHelperSvc thePartitionHelperSvc,
			Collection<String> theAdditionalDefaultOnlyTypes,
			Collection<String> theMergeWithDefaultTypes) {
		this.myPartitionHelperSvc = thePartitionHelperSvc;
		this.myAdditionalDefaultOnlyTypes = toLowerCaseSet(theAdditionalDefaultOnlyTypes);
		this.myMergeWithDefaultTypes = toLowerCaseSet(theMergeWithDefaultTypes);
	}

	private static Set<String> toLowerCaseSet(Collection<String> theTypes) {
		Set<String> lowered = new HashSet<>();
		if (theTypes != null) {
			for (String t : theTypes) {
				if (t != null && !t.isEmpty()) {
					lowered.add(t.toLowerCase(Locale.ROOT));
				}
			}
		}
		return Collections.unmodifiableSet(lowered);
	}

	@Override
	protected RequestPartitionId extractPartitionIdFromRequest(RequestDetails theRequestDetails) {
		if (theRequestDetails instanceof SystemRequestDetails) {
			return RequestPartitionId.defaultPartition();
		}
		// Widening only applies to reads — writes against /fhir/<tenant>/<Type>
		// must stay tenant-scoped so a misconfigured client cannot silently
		// mutate shared default-partition config.
		if (theRequestDetails.getRequestType() == RequestTypeEnum.GET) {
			String resourceType = theRequestDetails.getResourceName();
			if (resourceType != null) {
				String lower = resourceType.toLowerCase(Locale.ROOT);
				// Non-partitionable definitional types (HAPI-1318) can ONLY live in
				// DEFAULT — they must never be merged with a tenant partition.
				if (!myPartitionHelperSvc.isResourcePartitionable(resourceType)) {
					return RequestPartitionId.defaultPartition();
				}
				// Partitionable shared types that should be visible from BOTH the
				// tenant partition and DEFAULT in one read. Checked before the
				// default-only set so an entry in both lists merges rather than
				// hides the tenant's own instances.
				if (myMergeWithDefaultTypes.contains(lower)) {
					return mergeTenantWithDefault(theRequestDetails);
				}
				// Partitionable types served purely from DEFAULT.
				if (myAdditionalDefaultOnlyTypes.contains(lower)) {
					return RequestPartitionId.defaultPartition();
				}
			}
		}
		return super.extractPartitionIdFromRequest(theRequestDetails);
	}

	// Returns a RequestPartitionId spanning the URL tenant partition and DEFAULT,
	// so a GET reads the union of the tenant's own instances and the shared
	// default-partition ones. Read-by-id across both partitions assumes the
	// logical id is unique across them (true for our data: tenant ids are
	// server-assigned numerics, shared seeds use stable string ids).
	private RequestPartitionId mergeTenantWithDefault(RequestDetails theRequestDetails) {
		RequestPartitionId tenant;
		try {
			tenant = super.extractPartitionIdFromRequest(theRequestDetails);
		} catch (RuntimeException e) {
			// No resolvable tenant (e.g. a tenant-less URL) — fall back to the
			// prior default-only behaviour rather than propagating the error.
			return RequestPartitionId.defaultPartition();
		}
		if (tenant == null || tenant.isAllPartitions() || tenant.isDefaultPartition()) {
			return tenant;
		}
		List<String> names = new ArrayList<>();
		if (tenant.getPartitionNames() != null) {
			names.addAll(tenant.getPartitionNames());
		}
		if (!names.contains(JpaConstants.DEFAULT_PARTITION_NAME)) {
			names.add(JpaConstants.DEFAULT_PARTITION_NAME);
		}
		return RequestPartitionId.fromPartitionNames(names);
	}
}

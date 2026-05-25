package ca.uhn.fhir.jpa.starter.authnz.inbound.authorization;

import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.partition.IRequestPartitionHelperSvc;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.server.interceptor.partition.RequestTenantPartitionInterceptor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
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
// Finer-grained than the type list above is a per-(type, id) widening map
// (see hapi.fhir.partitioning.default-only-resource-ids). Only GETs at
// /fhir/<tenant>/<Type>/<id> for the exact (Type, id) pairs configured here
// resolve to DEFAULT. Use this for platform-shared singletons that share a
// resource type with per-tenant data (e.g. Basic/feature-flags is shared,
// but per-tenant Basic/sync-metadata-* must stay tenant-scoped, so we cannot
// widen all of Basic via default_only_resource_types).
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
	private final Map<String, Set<String>> myDefaultOnlyResourceIds;

	public SystemAwareRequestTenantPartitionInterceptor(IRequestPartitionHelperSvc thePartitionHelperSvc) {
		this(thePartitionHelperSvc, Collections.emptySet(), Collections.emptyMap());
	}

	public SystemAwareRequestTenantPartitionInterceptor(
			IRequestPartitionHelperSvc thePartitionHelperSvc,
			Collection<String> theAdditionalDefaultOnlyTypes) {
		this(thePartitionHelperSvc, theAdditionalDefaultOnlyTypes, Collections.emptyMap());
	}

	public SystemAwareRequestTenantPartitionInterceptor(
			IRequestPartitionHelperSvc thePartitionHelperSvc,
			Collection<String> theAdditionalDefaultOnlyTypes,
			Map<String, ? extends Collection<String>> theDefaultOnlyResourceIds) {
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

		Map<String, Set<String>> loweredIds = new HashMap<>();
		if (theDefaultOnlyResourceIds != null) {
			for (Map.Entry<String, ? extends Collection<String>> e : theDefaultOnlyResourceIds.entrySet()) {
				String type = e.getKey();
				Collection<String> ids = e.getValue();
				if (type == null || type.isEmpty() || ids == null) {
					continue;
				}
				Set<String> idSet = new HashSet<>();
				for (String id : ids) {
					if (id != null && !id.isEmpty()) {
						idSet.add(id.toLowerCase(Locale.ROOT));
					}
				}
				if (!idSet.isEmpty()) {
					loweredIds.put(type.toLowerCase(Locale.ROOT), Collections.unmodifiableSet(idSet));
				}
			}
		}
		this.myDefaultOnlyResourceIds = Collections.unmodifiableMap(loweredIds);
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
			if (resourceType != null
					&& (!myPartitionHelperSvc.isResourcePartitionable(resourceType)
							|| myAdditionalDefaultOnlyTypes.contains(resourceType.toLowerCase(Locale.ROOT)))) {
				return RequestPartitionId.defaultPartition();
			}
			// Per-(type, id) widening — narrower than the type list. Fast-path past
			// the lookup when no pairs are configured so behavior is byte-identical
			// to the pre-patch path.
			if (resourceType != null && !myDefaultOnlyResourceIds.isEmpty()) {
				Set<String> ids = myDefaultOnlyResourceIds.get(resourceType.toLowerCase(Locale.ROOT));
				if (ids != null && theRequestDetails.getId() != null) {
					String idPart = theRequestDetails.getId().getIdPart();
					if (idPart != null && ids.contains(idPart.toLowerCase(Locale.ROOT))) {
						return RequestPartitionId.defaultPartition();
					}
				}
			}
		}
		return super.extractPartitionIdFromRequest(theRequestDetails);
	}
}

package ca.uhn.fhir.jpa.starter.authnz.inbound.authorization.enforcer;

import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.AuthenticationOutcome;
import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.Permission;
import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.UserSessionDetail;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnifiedEnforcer {
    private final Map<Permission, Enforcer> enforcers = new HashMap<>();

    {
        enforcers.put(Permission.FHIR_ALL_READ, FhirEnforcer.FHIR_ALL_READ);
        enforcers.put(Permission.FHIR_READ_ALL_IN_COMPARTMENT, FhirEnforcer.FHIR_READ_ALL_IN_COMPARTMENT);
        enforcers.put(Permission.FHIR_READ_ALL_OF_TYPE, FhirEnforcer.FHIR_READ_ALL_OF_TYPE);
        enforcers.put(Permission.FHIR_READ_INSTANCE, FhirEnforcer.FHIR_READ_INSTANCE);
        enforcers.put(Permission.FHIR_READ_TYPE_IN_COMPARTMENT, FhirEnforcer.FHIR_READ_TYPE_IN_COMPARTMENT);
        enforcers.put(Permission.FHIR_ALL_WRITE, FhirEnforcer.FHIR_ALL_WRITE);
        enforcers.put(Permission.FHIR_WRITE_ALL_IN_COMPARTMENT, FhirEnforcer.FHIR_WRITE_ALL_IN_COMPARTMENT);
        enforcers.put(Permission.FHIR_WRITE_ALL_OF_TYPE, FhirEnforcer.FHIR_WRITE_ALL_OF_TYPE);
        enforcers.put(Permission.FHIR_WRITE_INSTANCE, FhirEnforcer.FHIR_WRITE_INSTANCE);
        enforcers.put(Permission.FHIR_WRITE_TYPE_IN_COMPARTMENT, FhirEnforcer.FHIR_WRITE_TYPE_IN_COMPARTMENT);
        enforcers.put(Permission.FHIR_ALL_DELETE, FhirEnforcer.FHIR_ALL_DELETE);
        enforcers.put(Permission.FHIR_DELETE_ALL_IN_COMPARTMENT, FhirEnforcer.FHIR_DELETE_ALL_IN_COMPARTMENT);
        enforcers.put(Permission.FHIR_DELETE_ALL_OF_TYPE, FhirEnforcer.FHIR_DELETE_ALL_OF_TYPE);
        enforcers.put(Permission.FHIR_DELETE_TYPE_IN_COMPARTMENT, FhirEnforcer.FHIR_DELETE_TYPE_IN_COMPARTMENT);
        enforcers.put(Permission.FHIR_TRANSACTION, FhirEnforcer.FHIR_TRANSACTION);
        enforcers.put(Permission.FHIR_BATCH, FhirEnforcer.FHIR_BATCH);
        enforcers.put(Permission.FHIR_CAPABILITIES, FhirEnforcer.FHIR_CAPABILITIES);
        enforcers.put(Permission.FHIR_EXTENDED_OPERATION_ON_ANY_INSTANCE, FhirEnforcer.FHIR_EXTENDED_OPERATION_ON_ANY_INSTANCE);
        enforcers.put(Permission.FHIR_EXTENDED_OPERATION_ON_ANY_INSTANCE_OF_TYPE, FhirEnforcer.FHIR_EXTENDED_OPERATION_ON_ANY_INSTANCE_OF_TYPE);
        enforcers.put(Permission.FHIR_EXTENDED_OPERATION_ON_SERVER, FhirEnforcer.FHIR_EXTENDED_OPERATION_ON_SERVER);
        enforcers.put(Permission.FHIR_EXTENDED_OPERATION_ON_TYPE, FhirEnforcer.FHIR_EXTENDED_OPERATION_ON_TYPE);
        enforcers.put(Permission.FHIR_EXTENDED_OPERATION_SUPERUSER, FhirEnforcer.FHIR_EXTENDED_OPERATION_SUPERUSER);
        enforcers.put(Permission.FHIR_GET_RESOURCE_COUNTS, FhirEnforcer.FHIR_GET_RESOURCE_COUNTS);
        enforcers.put(Permission.FHIR_MANUAL_VALIDATION, FhirEnforcer.FHIR_MANUAL_VALIDATION);
        enforcers.put(Permission.FHIR_META_OPERATIONS_SUPERUSER, FhirEnforcer.FHIR_META_OPERATIONS_SUPERUSER);
        enforcers.put(Permission.FHIR_OP_APPLY, FhirEnforcer.FHIR_OP_APPLY);
        enforcers.put(Permission.FHIR_OP_BINARY_ACCESS_READ, FhirEnforcer.FHIR_OP_BINARY_ACCESS_READ);
        enforcers.put(Permission.FHIR_OP_BINARY_ACCESS_WRITE, FhirEnforcer.FHIR_OP_BINARY_ACCESS_WRITE);
        enforcers.put(Permission.FHIR_OP_CARE_GAPS, FhirEnforcer.FHIR_OP_CARE_GAPS);
        enforcers.put(Permission.FHIR_OP_COLLECTDATA, FhirEnforcer.FHIR_OP_COLLECTDATA);
        enforcers.put(Permission.FHIR_OP_SUBMIT_DATA, FhirEnforcer.FHIR_OP_SUBMIT_DATA);
        enforcers.put(Permission.FHIR_PATCH, FhirEnforcer.FHIR_PATCH);
        enforcers.put(Permission.FHIR_OP_EVALUATE_MEASURE, FhirEnforcer.FHIR_OP_EVALUATE_MEASURE);
        enforcers.put(Permission.FHIR_OP_ENCOUNTER_EVERYTHING, FhirEnforcer.FHIR_OP_ENCOUNTER_EVERYTHING);
        enforcers.put(Permission.FHIR_OP_EXTRACT, FhirEnforcer.FHIR_OP_EXTRACT);
        enforcers.put(Permission.FHIR_OP_POPULATE, FhirEnforcer.FHIR_OP_POPULATE);
        enforcers.put(Permission.FHIR_OP_PACKAGE, FhirEnforcer.FHIR_OP_PACKAGE);
        enforcers.put(Permission.FHIR_OP_MEMBER_MATCH, FhirEnforcer.FHIR_OP_MEMBER_MATCH);
        enforcers.put(Permission.FHIR_OP_PATIENT_EVERYTHING_ACCESS_ALL, FhirEnforcer.FHIR_OP_PATIENT_EVERYTHING_ACCESS_ALL);
        enforcers.put(Permission.FHIR_OP_PATIENT_MATCH, FhirEnforcer.FHIR_OP_PATIENT_MATCH);
        enforcers.put(Permission.FHIR_OP_PATIENT_SUMMARY, FhirEnforcer.FHIR_OP_PATIENT_SUMMARY);
        enforcers.put(Permission.FHIR_OP_PATIENT_EVERYTHING, FhirEnforcer.FHIR_OP_PATIENT_EVERYTHING);
        enforcers.put(Permission.FHIR_OP_STRUCTUREDEFINITION_SNAPSHOT, FhirEnforcer.FHIR_OP_STRUCTUREDEFINITION_SNAPSHOT);
        enforcers.put(Permission.FHIR_PROCESS_MESSAGE, FhirEnforcer.FHIR_PROCESS_MESSAGE);
        enforcers.put(Permission.FHIR_TRIGGER_SUBSCRIPTION, FhirEnforcer.FHIR_TRIGGER_SUBSCRIPTION);
        enforcers.put(Permission.FHIR_UPDATE_REWRITE_HISTORY, FhirEnforcer.FHIR_UPDATE_REWRITE_HISTORY);
        enforcers.put(Permission.FHIR_TERMINOLOGY_READ, FhirEnforcer.FHIR_TERMINOLOGY_READ);
    }

    public IAuthRuleBuilder enforce(AuthenticationOutcome outcome, List<UserSessionDetail.GrantedAuthority> authorities, IAuthRuleBuilder ruleBuilder) {
        var localRuleBuilder = ruleBuilder;
        for (UserSessionDetail.GrantedAuthority authority : authorities) {
            var enforcer = enforcers.get(authority.getPermission());
            if (enforcer != null) {
                localRuleBuilder = enforcer.enforce(outcome, authority, localRuleBuilder);
            }
        }
        return localRuleBuilder;
    }
}
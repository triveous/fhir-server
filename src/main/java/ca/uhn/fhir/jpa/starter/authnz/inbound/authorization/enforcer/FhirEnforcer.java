package ca.uhn.fhir.jpa.starter.authnz.inbound.authorization.enforcer;

import ca.uhn.fhir.jpa.starter.authnz.inbound.shared.Permission;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;

import java.util.List;

import static ca.uhn.fhir.jpa.starter.authnz.inbound.authorization.enforcer.EnforcerUtils.*;


public interface FhirEnforcer {
    Enforcer FHIR_ALL_READ = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .read()
            .allResources()
            .withAnyId()
            .andThen();

    Enforcer FHIR_READ_ALL_IN_COMPARTMENT = (outcome, authority, ruleBuilder) -> chain(ruleBuilder, extractIds(authority.getArgument()), (i) -> ruleBuilder
            .allow(authority.getPermission().name())
            .read()
            .allResources()
            .inCompartment(i.getResourceType(), i));

    Enforcer FHIR_READ_ALL_OF_TYPE = (outcome, authority, ruleBuilder) -> chain(ruleBuilder, extractResourceType(authority.getArgument()), (t) -> ruleBuilder
            .allow(authority.getPermission().name())
            .read()
            .resourcesOfType(t)
            .withAnyId());

    Enforcer FHIR_READ_INSTANCE = (outcome, authority, builder) -> chain(builder, extractIds(authority.getArgument()), (i) -> builder
            .allow(authority.getPermission().name())
            .read()
            .instance(i));

    Enforcer FHIR_READ_TYPE_IN_COMPARTMENT = (outcome, authority, ruleBuilder) -> chain(ruleBuilder, extractResourceAndIds(authority.getArgument()), (i) -> ruleBuilder
            .allow(authority.getPermission().name())
            .read()
            .resourcesOfType(i.getLeft())
            .inCompartment(i.getRight().getResourceType(), i.getRight()));

    Enforcer FHIR_ALL_WRITE = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .write()
            .allResources()
            .withAnyId()
            .andThen();

    Enforcer FHIR_WRITE_ALL_IN_COMPARTMENT = (outcome, authority, ruleBuilder) -> chain(ruleBuilder, extractIds(authority.getArgument()), (i) -> ruleBuilder
            .allow(authority.getPermission().name())
            .write()
            .allResources()
            .inCompartment(i.getResourceType(), i));

    Enforcer FHIR_WRITE_ALL_OF_TYPE = (outcome, authority, ruleBuilder) -> chain(ruleBuilder, extractResourceType(authority.getArgument()), (t) -> ruleBuilder
            .allow(authority.getPermission().name())
            .write()
            .resourcesOfType(t)
            .withAnyId());

    Enforcer FHIR_WRITE_INSTANCE = (outcome, authority, builder) -> chain(builder, extractIds(authority.getArgument()), (i) -> builder
            .allow(authority.getPermission().name())
            .write()
            .instance(i));

    Enforcer FHIR_WRITE_TYPE_IN_COMPARTMENT = (outcome, authority, ruleBuilder) -> chain(ruleBuilder, extractResourceAndIds(authority.getArgument()), (i) -> ruleBuilder
            .allow(authority.getPermission().name())
            .write()
            .resourcesOfType(i.getLeft())
            .inCompartment(i.getRight().getResourceType(), i.getRight()));

    Enforcer FHIR_ALL_DELETE = (outcome, authority, ruleBuilder) -> {
        var deleteRuleBuilder = ruleBuilder
                .allow(authority.getPermission().name())
                .delete()
                .allResources()
                .withAnyId()
                .andThen();

        var cascadeDelete = outcome.hasPermission(Permission.FHIR_DELETE_CASCADE_ALLOWED);
        if (cascadeDelete) {
            deleteRuleBuilder = deleteRuleBuilder
                    .allow(authority.getPermission().name())
                    .delete()
                    .onCascade()
                    .allResources()
                    .withAnyId()
                    .andThen();
        }

        return deleteRuleBuilder;
    };

    Enforcer FHIR_DELETE_ALL_IN_COMPARTMENT = (outcome, authority, ruleBuilder) -> {

        var deleteRuleBuilder = chain(ruleBuilder, extractIds(authority.getArgument()), i -> ruleBuilder
                .allow(authority.getPermission().name())
                .delete()
                .allResources()
                .inCompartment(i.getResourceType(), i));

        var cascadeDelete = outcome.hasPermission(Permission.FHIR_DELETE_CASCADE_ALLOWED);
        if (cascadeDelete) {
            deleteRuleBuilder = chain(deleteRuleBuilder, extractIds(authority.getArgument()), i -> ruleBuilder
                    .allow(authority.getPermission().name())
                    .delete()
                    .onCascade()
                    .allResources()
                    .inCompartment(i.getResourceType(), i));
        }

        return deleteRuleBuilder;
    };

    Enforcer FHIR_DELETE_ALL_OF_TYPE = (outcome, authority, ruleBuilder) -> {
        var deleteRuleBuilder = chain(ruleBuilder, extractResourceType(authority.getArgument()), t -> ruleBuilder
                .allow(authority.getPermission().name())
                .delete()
                .resourcesOfType(t)
                .withAnyId());

        var cascadeDelete = outcome.hasPermission(Permission.FHIR_DELETE_CASCADE_ALLOWED);
        if (cascadeDelete) {
            deleteRuleBuilder = chain(deleteRuleBuilder, extractResourceType(authority.getArgument()), t -> ruleBuilder
                    .allow(authority.getPermission().name())
                    .delete()
                    .onCascade()
                    .resourcesOfType(t)
                    .withAnyId());
        }

        return deleteRuleBuilder;
    };

    Enforcer FHIR_DELETE_TYPE_IN_COMPARTMENT = (outcome, authority, ruleBuilder) -> {
        var deleteRuleBuilder = chain(ruleBuilder, extractResourceAndIds(authority.getArgument()), i -> ruleBuilder
                .allow(authority.getPermission().name())
                .delete()
                .resourcesOfType(i.getLeft())
                .inCompartment(i.getRight().getResourceType(), i.getRight()));

        var cascadeDelete = outcome.hasPermission(Permission.FHIR_DELETE_CASCADE_ALLOWED);
        if (cascadeDelete) {
            deleteRuleBuilder = chain(deleteRuleBuilder, extractResourceAndIds(authority.getArgument()), i -> ruleBuilder
                    .allow(authority.getPermission().name())
                    .delete()
                    .onCascade()
                    .resourcesOfType(i.getLeft())
                    .inCompartment(i.getRight().getResourceType(), i.getRight()));
        }

        return deleteRuleBuilder;
    };

    Enforcer FHIR_TRANSACTION = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .transaction()
            .withAnyOperation()
            .andApplyNormalRules()
            .andThen();
    Enforcer FHIR_BATCH = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .transaction()
            .withAnyOperation()
            .andApplyNormalRules()
            .andThen();

    Enforcer FHIR_CAPABILITIES = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .metadata()
            .andThen()
            .allow()
            .read()
            .resourcesOfType(CapabilityStatement.class)
            .withAnyId()
            .andThen();

    Enforcer FHIR_EXTENDED_OPERATION_ON_ANY_INSTANCE = (outcome, authority, ruleBuilder) -> chain(ruleBuilder, authority.getArgument(), (op) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named(op)
            .onAnyInstance()
            .andAllowAllResponses()
    );

    Enforcer FHIR_EXTENDED_OPERATION_ON_ANY_INSTANCE_OF_TYPE = (outcome, authority, ruleBuilder) -> chain(ruleBuilder, extractResourceAndOp(authority.getArgument()), (op) -> {
        try {
            //noinspection unchecked
            return ruleBuilder
                    .allow(authority.getPermission().name())
                    .operation()
                    .named(op.getRight())
                    .onInstancesOfType((Class<? extends IBaseResource>) Class.forName(String.format("org.hl7.fhir.r4.model.%s", op.getLeft())))
                    .andAllowAllResponses();
        } catch (ClassNotFoundException e) {
            return null;
        }
    });

    Enforcer FHIR_EXTENDED_OPERATION_ON_SERVER = (outcome, authority, ruleBuilder) -> chain(ruleBuilder, authority.getArgument(), (op) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named(op)
            .onServer()
            .andAllowAllResponsesWithAllResourcesAccess()
    );

    Enforcer FHIR_EXTENDED_OPERATION_ON_TYPE = (outcome, authority, ruleBuilder) -> chain(ruleBuilder, extractResourceAndOp(authority.getArgument()), (op) -> {
                try {
                    //noinspection unchecked
                    return ruleBuilder
                            .allow(authority.getPermission().name())
                            .operation()
                            .named(op.getRight())
                            .onType((Class<? extends IBaseResource>) Class.forName(String.format("org.hl7.fhir.r4.model.%s", op.getLeft())))
                            .andAllowAllResponsesWithAllResourcesAccess();
                } catch (ClassNotFoundException e) {
                    return null;
                }
            }
    );

    // Doesn't apply to $expunge and $delete-expunge
    Enforcer FHIR_EXTENDED_OPERATION_SUPERUSER = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .withAnyName()
            .onServer()
            .andAllowAllResponsesWithAllResourcesAccess()
            .andThen();

    Enforcer FHIR_GET_RESOURCE_COUNTS = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$get-resource-counts")
            .onServer()
            .andAllowAllResponsesWithAllResourcesAccess()
            .andThen();

    Enforcer FHIR_GRAPHQL = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .graphQL()
            .any()
            .andThen();

    Enforcer FHIR_MANUAL_VALIDATION = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$validate")
            .onAnyType()
            .andAllowAllResponses()
            .andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$validate")
            .onAnyInstance()
            .andAllowAllResponses()
            .andThen();

    Enforcer FHIR_META_OPERATIONS_SUPERUSER = (outcome, authority, ruleBuilder) -> {
        var localBuilder = ruleBuilder;
        localBuilder = chain(ruleBuilder, List.of("$meta"), (op) -> ruleBuilder
                .allow(authority.getPermission().name())
                .operation()
                .named(op)
                .onAnyType()
                .andRequireExplicitResponseAuthorization()
                .andThen()
                .allow()
                .operation()
                .named(op)
                .onAnyInstance()
                .andRequireExplicitResponseAuthorization()
                .andThen()
                .allow()
                .operation()
                .named(op)
                .onServer()
                .andRequireExplicitResponseAuthorization());

        localBuilder = chain(ruleBuilder, List.of("$meta-add", "$meta-delete"), (op) -> ruleBuilder
                .allow()
                .operation()
                .named(op)
                .onAnyInstance()
                .andRequireExplicitResponseAuthorization());

        return localBuilder;
    };

    Enforcer FHIR_OP_APPLY = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$apply")
            .onInstancesOfType(PlanDefinition.class)
            .andRequireExplicitResponseAuthorization()
            .andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$apply")
            .onType(PlanDefinition.class)
            .andRequireExplicitResponseAuthorization()
            .andThen()

            .allow(authority.getPermission().name())
            .operation()
            .named("$apply")
            .onInstancesOfType(ActivityDefinition.class)
            .andRequireExplicitResponseAuthorization()
            .andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$apply")
            .onType(ActivityDefinition.class)
            .andRequireExplicitResponseAuthorization()
            .andThen()

            .allow(authority.getPermission().name())
            .operation()
            .named("$apply")
            .onInstancesOfType(SpecimenDefinition.class)
            .andRequireExplicitResponseAuthorization()
            .andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$apply")
            .onType(SpecimenDefinition.class)
            .andRequireExplicitResponseAuthorization()
            .andThen()

            .allow(authority.getPermission().name())
            .operation()
            .named("$apply")
            .onInstancesOfType(ChargeItemDefinition.class)
            .andRequireExplicitResponseAuthorization()
            .andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$apply")
            .onType(ChargeItemDefinition.class)
            .andRequireExplicitResponseAuthorization()
            .andThen();

    Enforcer FHIR_OP_BINARY_ACCESS_READ = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$binary-access-read")
            .onInstancesOfType(DocumentReference.class)
            .andRequireExplicitResponseAuthorization()
            .andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$binary-access-read")
            .onInstancesOfType(Binary.class)
            .andRequireExplicitResponseAuthorization()
            .andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$binary-access-read")
            .onInstancesOfType(Media.class)
            .andRequireExplicitResponseAuthorization()
            .andThen();

    Enforcer FHIR_OP_BINARY_ACCESS_WRITE = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$binary-access-write")
            .onInstancesOfType(DocumentReference.class)
            .andRequireExplicitResponseAuthorization()
            .andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$binary-access-write")
            .onInstancesOfType(Binary.class)
            .andRequireExplicitResponseAuthorization()
            .andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$binary-access-write")
            .onInstancesOfType(Media.class)
            .andRequireExplicitResponseAuthorization()
            .andThen();

    Enforcer FHIR_OP_CARE_GAPS = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$care-gaps")
            .onType(Measure.class)
            .andRequireExplicitResponseAuthorization()
            .andThen();

    Enforcer FHIR_OP_COLLECTDATA = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$collect-data")
            .onType(Measure.class)
            .andRequireExplicitResponseAuthorization()
            .andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$collect-data")
            .onInstancesOfType(Measure.class)
            .andRequireExplicitResponseAuthorization()
            .andThen();

    Enforcer FHIR_OP_SUBMIT_DATA = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$submit-data")
            .onType(Measure.class)
            .andRequireExplicitResponseAuthorization()
            .andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$submit-data")
            .onInstancesOfType(Measure.class)
            .andRequireExplicitResponseAuthorization()
            .andThen();

    Enforcer FHIR_OP_EVALUATE_MEASURE = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$evaluate-measure")
            .onType(Measure.class)
            .andRequireExplicitResponseAuthorization()
            .andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$evaluate-measure")
            .onInstancesOfType(Measure.class)
            .andRequireExplicitResponseAuthorization()
            .andThen();

    Enforcer FHIR_OP_ENCOUNTER_EVERYTHING = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$everything")
            .onInstancesOfType(Encounter.class)
            .andRequireExplicitResponseAuthorization()
            .andThen();

    Enforcer FHIR_OP_EXTRACT = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$extract")
            .onInstancesOfType(QuestionnaireResponse.class)
            .andRequireExplicitResponseAuthorization().andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$extract")
            .onType(QuestionnaireResponse.class)
            .andRequireExplicitResponseAuthorization()
            .andThen();

    Enforcer FHIR_OP_PACKAGE = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$package")
            .onType(Questionnaire.class)
            .andRequireExplicitResponseAuthorization().andThen();

    Enforcer FHIR_OP_MEMBER_MATCH = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$member-match")
            .onType(Patient.class)
            .andRequireExplicitResponseAuthorization().andThen();

    Enforcer FHIR_OP_PATIENT_EVERYTHING = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$everything")
            .onInstancesOfType(Patient.class)
            .andRequireExplicitResponseAuthorization().andThen();

    Enforcer FHIR_OP_PATIENT_EVERYTHING_ACCESS_ALL = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$everything")
            .onInstancesOfType(Patient.class)
            .andAllowAllResponsesWithAllResourcesAccess().andThen();

    Enforcer FHIR_OP_PATIENT_MATCH = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$match")
            .onType(Patient.class)
            .andAllowAllResponses().andThen();

    Enforcer FHIR_OP_PATIENT_SUMMARY = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$summary")
            .onInstancesOfType(Patient.class)
            .andRequireExplicitResponseAuthorization().andThen();

    Enforcer FHIR_OP_POPULATE = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$populate")
            .onInstancesOfType(Questionnaire.class)
            .andRequireExplicitResponseAuthorization().andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$populate")
            .onType(Questionnaire.class)
            .andRequireExplicitResponseAuthorization().andThen();

    Enforcer FHIR_OP_STRUCTUREDEFINITION_SNAPSHOT = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$snapshot")
            .onInstancesOfType(StructureDefinition.class)
            .andRequireExplicitResponseAuthorization().andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$snapshot")
            .onType(StructureDefinition.class)
            .andRequireExplicitResponseAuthorization().andThen();

    Enforcer FHIR_PATCH = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .patch()
            .allRequests()
            .andThen();

    Enforcer FHIR_PROCESS_MESSAGE = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$process-message")
            .onServer()
            .andAllowAllResponses().andThen();

    Enforcer FHIR_TRIGGER_SUBSCRIPTION = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$trigger-subscription")
            .onInstancesOfType(Subscription.class)
            .andAllowAllResponses().andThen();

    Enforcer FHIR_UPDATE_REWRITE_HISTORY = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .updateHistoryRewrite()
            .allRequests()
            .andThen();

    Enforcer FHIR_TERMINOLOGY_READ = (outcome, authority, ruleBuilder) -> ruleBuilder
            .allow(authority.getPermission().name())
            .operation()
            .named("$lookup")
            .onType(CodeSystem.class)
            .andRequireExplicitResponseAuthorization().andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$validate-code")
            .onType(CodeSystem.class)
            .andRequireExplicitResponseAuthorization().andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$lookup")
            .onInstancesOfType(CodeSystem.class)
            .andRequireExplicitResponseAuthorization().andThen()
            .allow(authority.getPermission().name())
            .operation()
            .named("$validate-code")
            .onInstancesOfType(CodeSystem.class)
            .andRequireExplicitResponseAuthorization().andThen()
            .allow(authority.getPermission().name())
            .read()
            .resourcesOfType(ValueSet.class)
            .withAnyId().andThen()
            .allow(authority.getPermission().name())
            .read()
            .resourcesOfType(CodeSystem.class)
            .withAnyId().andThen()
            .allow(authority.getPermission().name())
            .read()
            .resourcesOfType(ConceptMap.class)
            .withAnyId().andThen();
}

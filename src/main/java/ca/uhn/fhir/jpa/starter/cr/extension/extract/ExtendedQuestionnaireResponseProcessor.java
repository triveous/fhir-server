package ca.uhn.fhir.jpa.starter.cr.extension.extract;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.*;
import org.hl7.fhir.r4.model.CanonicalType;
import org.opencds.cqf.fhir.api.Repository;
import org.opencds.cqf.fhir.cql.EvaluationSettings;
import org.opencds.cqf.fhir.cql.LibraryEngine;
import org.opencds.cqf.fhir.cr.questionnaireresponse.QuestionnaireResponseProcessor;
import org.opencds.cqf.fhir.cr.questionnaireresponse.extract.IExtractProcessor;
import org.opencds.cqf.fhir.utility.SearchHelper;
import org.opencds.cqf.fhir.utility.monad.Either;

@SuppressWarnings("UnstableApiUsage")
public class ExtendedQuestionnaireResponseProcessor extends QuestionnaireResponseProcessor {
	public ExtendedQuestionnaireResponseProcessor(Repository repository) {
		this(repository, EvaluationSettings.getDefault());
	}

	public ExtendedQuestionnaireResponseProcessor(Repository repository, EvaluationSettings evaluationSettings) {
		this(repository, evaluationSettings, null);
	}

	public ExtendedQuestionnaireResponseProcessor(Repository repository, EvaluationSettings evaluationSettings, IExtractProcessor extractProcessor) {
		super(repository, evaluationSettings, extractProcessor);
		this.extractProcessor = extractProcessor != null ? extractProcessor : new ExtendedExtractProcessor();
	}


	@SuppressWarnings("unchecked")
	protected IBaseResource resolveStructureMap(IBaseResource questionnaire) {
		try {
			IPrimitiveType<String> canonical;
			if (true) {
				canonical = new CanonicalType("https://midas.iisc.ac.in/fhir/StructureMap/OralCancerScreeningResponse");
			}
			return canonical == null
				? null
				: SearchHelper.searchRepositoryByCanonical(
				repository,
				canonical,
				repository
					.fhirContext()
					.getResourceDefinition("structuremap")
					.getImplementingClass());
		} catch (FHIRException e) {
			logger.error(e.getMessage());
			return null;
		}
	}


	@Override
	public <R extends IBaseResource> IBaseBundle extract(Either<IIdType, R> resource, IBaseParameters parameters, IBaseBundle bundle, LibraryEngine libraryEngine) {
		var questionnaireResponse = resolveQuestionnaireResponse(resource);
		var questionnaire = resolveQuestionnaire(questionnaireResponse);
		var structureMap = resolveStructureMap(questionnaire);
		var subject = (IBaseReference) modelResolver.resolvePath(questionnaireResponse, "subject");
		var request = new ExtendedExtractRequest(
			questionnaireResponse,
			questionnaire,
			structureMap,
			subject == null ? null : subject.getReferenceElement(),
			parameters,
			bundle,
			libraryEngine,
			modelResolver,
			repository.fhirContext());
		return extractProcessor.extract(request);
	}
}

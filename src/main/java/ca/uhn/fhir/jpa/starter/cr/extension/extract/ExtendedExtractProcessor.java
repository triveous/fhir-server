package ca.uhn.fhir.jpa.starter.cr.extension.extract;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.opencds.cqf.fhir.cr.questionnaireresponse.extract.ExtractProcessor;
import org.opencds.cqf.fhir.cr.questionnaireresponse.extract.ExtractRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExtendedExtractProcessor extends ExtractProcessor {
	private final StructureMapExtractor structureMapExtractor;

	public ExtendedExtractProcessor() {
		structureMapExtractor = new StructureMapExtractor();
	}

	@Override
	public List<IBaseResource> processItems(ExtractRequest request) {
		if (getStructureMap(request) == null) return super.processItems(request);

		var subject = (IBaseReference) request.resolvePath(request.getQuestionnaireResponse(), "subject");
		var resources = new ArrayList<IBaseResource>();
		try {
			structureMapExtractor.process(request, resources, subject);
		} catch (IOException e) {
			logger.error("Unable to transform questionnaire using StructureMap", e);
		}
		return resources;
	}

	private IBaseResource getStructureMap(ExtractRequest request) {
		if (request instanceof ExtendedExtractRequest) {
			return ((ExtendedExtractRequest) request).getStructureMap();
		}
		return null;
	}
}

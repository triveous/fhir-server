package ca.uhn.fhir.jpa.starter.cr.extension.extract;

import ca.uhn.fhir.context.FhirVersionEnum;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.context.SimpleWorkerContext;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StructureMap;
import org.hl7.fhir.r4.utils.StructureMapUtilities;
import org.opencds.cqf.fhir.cr.questionnaireresponse.extract.ExtractRequest;
import org.opencds.cqf.fhir.utility.BundleHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class StructureMapExtractor {
	protected static final Logger logger = LoggerFactory.getLogger(StructureMapExtractor.class);

	public void process(ExtractRequest request, List<IBaseResource> resources, IBaseReference subject) throws IOException {
		if (Objects.requireNonNull(request.getFhirVersion()) == FhirVersionEnum.R4) {
			processR4(request, resources, subject);
		}
	}

	private static void processR4(ExtractRequest request, List<IBaseResource> resources, IBaseReference subject) throws IOException {
		if (!(request instanceof ExtendedExtractRequest)) return;

		var context = SimpleWorkerContext.fromNothing();
		var parameters = request.getParameters() == null ? new Parameters() : (Parameters) request.getParameters();
		context.setExpansionProfile(parameters);
		var sm = new StructureMapUtilities(context);
		var bundle = (Bundle) BundleHelper.newBundle(request.getFhirVersion());
		sm.transform(context, (Base) request.getQuestionnaireResponse(), (StructureMap) ((ExtendedExtractRequest) request).getStructureMap(), bundle);
		resources.addAll(bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource).collect(Collectors.toList()));
	}
}

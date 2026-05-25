package ca.uhn.fhir.jpa.starter.binary;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.jpa.binary.api.StoredDetails;
import ca.uhn.fhir.jpa.binary.svc.BaseBinaryStorageSvcImpl;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.base.Charsets;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Date;

// Multi-tenant GCS storage: each request resolves to a per-partition bucket
// (template like "aa-stg-{partition}-fhir-media"). The HAPI BaseBinaryStorageSvcImpl
// surface only passes RequestDetails to storeBinaryContent; the other four methods
// run inside the same servlet request thread but receive no RequestDetails, so we
// pull the partition out of the request URL via Spring's RequestContextHolder.
// URL-based tenant routing is configured in StarterJpaConfig
// (UrlBaseTenantIdentificationStrategy) — the partition is always the path
// segment immediately after the FHIR servlet base.
public class GcsBinaryStorageSvc extends BaseBinaryStorageSvcImpl {
	private static final Logger log = LoggerFactory.getLogger(GcsBinaryStorageSvc.class);
	private static final String PARTITION_PLACEHOLDER = "{partition}";
	private static final String DEFAULT_PARTITION = "DEFAULT";

	private final String bucketNameTemplate;
	private final String basePath;
	private final ObjectMapper objectMapper;
	private final Storage storage;

	public GcsBinaryStorageSvc(String bucketNameTemplate, String basePath, ObjectMapper objectMapper) {
		assert bucketNameTemplate != null;
		assert bucketNameTemplate.contains(PARTITION_PLACEHOLDER);
		this.bucketNameTemplate = bucketNameTemplate;
		this.basePath = basePath == null ? "" : basePath;
		this.objectMapper = objectMapper;
		this.storage = StorageOptions.getDefaultInstance().getService();
	}

	public boolean isValidBinaryContentId(String theNewBinaryContentId) {
		return !StringUtils.containsAny(theNewBinaryContentId, '\\', '/', '|', '.');
	}

	@NotNull
	@Override
	public StoredDetails storeBinaryContent(IIdType theResourceId, String theBlobIdOrNull, String theContentType, InputStream inputStream, RequestDetails requestDetails) throws IOException {
		String bucket = resolveBucketFromRequestDetails(requestDetails);
		String id = super.provideIdForNewBinaryContent(theBlobIdOrNull, null, requestDetails, theContentType);
		ResolvedPath storagePath = getStoragePath(id);
		ResolvedPath storageFilename = getStorageFilename(storagePath, theResourceId, id);

		log.info("Writing to gs://{}/{}", bucket, storageFilename.getFullPath());
		var countingInputStream = this.createCountingInputStream(inputStream);
		//noinspection UnstableApiUsage
		var hashingInputStream = this.createHashingInputStream(countingInputStream);
		write(bucket, storageFilename, hashingInputStream, theContentType);

		long count = countingInputStream.getByteCount();
		//noinspection UnstableApiUsage
		StoredDetails details = new StoredDetails(id, count, theContentType, hashingInputStream, new Date());
		ResolvedPath descriptorFile = this.getDescriptorFilename(storagePath, theResourceId, id);
		var storedDetailJson = objectMapper.writeValueAsString(details);

		log.info("Writing to gs://{}/{}", bucket, descriptorFile.getFullPath());
		write(bucket, descriptorFile, new ByteArrayInputStream(storedDetailJson.getBytes()), "application/json");

		log.info("Stored binary blob with {} bytes and ContentType {} for resource {}", count, theContentType, theResourceId);
		return details;
	}

	@Override
	public StoredDetails fetchBinaryContentDetails(IIdType theResourceId, String theBlobId) throws IOException {
		String bucket = resolveBucket();
		var storagePath = this.getStoragePath(theBlobId);
		if (storagePath == null) return null;

		var descriptorFile = getDescriptorFilename(storagePath, theResourceId, theBlobId);
		if (!objectExist(bucket, descriptorFile)) return null;

		var descriptorFileStream = read(bucket, descriptorFile);
		if (descriptorFileStream == null) throw translateError(new Exception("Unable to load"));

		try (var reader = new InputStreamReader(descriptorFileStream, Charsets.UTF_8)) {
			return objectMapper.readValue(reader, StoredDetails.class);
		} catch (Exception e) {
			throw translateError(new Exception("Unable to load"));
		}
	}

	@Override
	public boolean writeBinaryContent(IIdType theResourceId, String theBlobId, OutputStream theOutputStream) throws IOException {
		String bucket = resolveBucket();
		var inputStream = this.getInputStream(bucket, theResourceId, theBlobId);
		if (inputStream == null) return false;

		try (theOutputStream) {
			IOUtils.copy(inputStream, theOutputStream);
		} catch (IOException e) {
			try {
				inputStream.close();
			} catch (IOException ex) {
				e.addSuppressed(ex);
			}
		}

		return false;
	}

	@Override
	public void expungeBinaryContent(IIdType theResourceId, String theBlobId) {
		String bucket = resolveBucket();
		ResolvedPath storagePath = this.getStoragePath(theBlobId);
		if (storagePath == null) return;

		ResolvedPath storageFile = this.getStorageFilename(storagePath, theResourceId, theBlobId);
		if (objectExist(bucket, storageFile)) remove(bucket, storageFile);

		var descriptorFile = this.getDescriptorFilename(storagePath, theResourceId, theBlobId);
		if (objectExist(bucket, descriptorFile)) remove(bucket, descriptorFile);
	}

	@Override
	public byte[] fetchBinaryContent(IIdType theResourceId, String theBlobId) throws IOException {
		String bucket = resolveBucket();
		var details = fetchBinaryContentDetails(theResourceId, theBlobId);
		try (InputStream inputStream = getInputStream(bucket, theResourceId, theBlobId)) {
			if (inputStream != null) {
				return IOUtils.toByteArray(inputStream, details.getBytes());
			}
		}

		throw new ResourceNotFoundException(
			Msg.code(1327) + "Unknown blob ID: " + theBlobId + " for resource ID " + theResourceId);
	}

	@Nullable
	private InputStream getInputStream(String bucket, IIdType theResourceId, String theBlobId) {
		ResolvedPath storagePath = this.getStoragePath(theBlobId);
		if (storagePath == null) return null;
		var file = this.getStorageFilename(storagePath, theResourceId, theBlobId);
		return objectExist(bucket, file) ? read(bucket, file) : null;
	}

	private String resolveBucketFromRequestDetails(RequestDetails requestDetails) {
		String tenantId = requestDetails == null ? null : requestDetails.getTenantId();
		return resolveBucketForPartition(tenantId);
	}

	// Called from inside a HAPI REST request thread; relies on Spring's request
	// attributes being bound by the servlet dispatch (DispatcherServlet /
	// RequestContextListener / RequestContextFilter — Spring Boot wires this by default).
	private String resolveBucket() {
		var attrs = RequestContextHolder.currentRequestAttributes();
		if (!(attrs instanceof ServletRequestAttributes)) {
			throw new InvalidRequestException(
				"GCS binary storage requires an active HTTP request to resolve the per-tenant bucket");
		}
		HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();
		String tenantId = extractTenantFromRequest(request);
		return resolveBucketForPartition(tenantId);
	}

	private String resolveBucketForPartition(String partition) {
		if (StringUtils.isBlank(partition) || DEFAULT_PARTITION.equalsIgnoreCase(partition)) {
			throw new InvalidRequestException(
				"GCS binary storage is not supported for the DEFAULT partition; use a tenant-scoped URL");
		}
		return bucketNameTemplate.replace(PARTITION_PLACEHOLDER, partition);
	}

	// URL layout under UrlBaseTenantIdentificationStrategy: <ctx>/fhir/<tenant>/<Resource>/...
	// We walk the path tokens, skip the servlet/context prefix up to "fhir", and take
	// the next token as the partition name.
	private static String extractTenantFromRequest(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (path == null) return null;
		String[] parts = StringUtils.split(path, '/');
		if (parts == null) return null;
		for (int i = 0; i < parts.length - 1; i++) {
			if ("fhir".equalsIgnoreCase(parts[i])) {
				return parts[i + 1];
			}
		}
		return null;
	}

	private static BaseServerResponseException translateError(Exception e) {
		log.error("GCS binary storage error", e);
		var outcome = new OperationOutcome();
		outcome.addIssue()
			.setSeverity(OperationOutcome.IssueSeverity.FATAL)
			.setCode(OperationOutcome.IssueType.UNKNOWN);
		return new InternalErrorException("Unable to write", outcome);
	}

	private Boolean objectExist(String bucket, ResolvedPath path) {
		try {
			Blob blob = storage.get(BlobId.of(bucket, path.getFullPath()));
			return blob != null && blob.exists();
		} catch (Exception e) {
			throw translateError(e);
		}
	}

	private InputStream read(String bucket, ResolvedPath path) {
		try {
			Blob blob = storage.get(BlobId.of(bucket, path.getFullPath()));
			if (blob == null || !blob.exists()) return null;
			return java.nio.channels.Channels.newInputStream(blob.reader());
		} catch (Exception e) {
			throw translateError(e);
		}
	}

	private void write(String bucket, ResolvedPath resolvedPath, InputStream stream, String contentType) {
		BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, resolvedPath.getFullPath()))
			.setContentType(contentType)
			.build();
		try {
			byte[] data = IOUtils.toByteArray(stream);
			storage.create(blobInfo, data);
		} catch (Exception e) {
			throw translateError(e);
		}
	}

	private void remove(String bucket, ResolvedPath resolvedPath) {
		try {
			storage.delete(BlobId.of(bucket, resolvedPath.getFullPath()));
		} catch (Exception e) {
			throw translateError(e);
		}
	}

	private ResolvedPath getStoragePath(String theId) {
		return new ResolvedPath(basePath, theId);
	}

	private ResolvedPath getStorageFilename(ResolvedPath theStoragePath, IIdType theResourceId, String theId) {
		return this.getStorageFilename(theStoragePath, theResourceId, theId, ".bin");
	}

	private ResolvedPath getStorageFilename(ResolvedPath theStoragePath, IIdType theResourceId, String theId, String theExtension) {
		Validate.notBlank(theResourceId.getResourceType());
		Validate.notBlank(theResourceId.getIdPart());
		String resourceType = theResourceId.getResourceType();
		String filename = resourceType + "_" + theResourceId.getIdPart() + "_" + theId;
		return new ResolvedPath(theStoragePath, filename + theExtension);
	}

	@Nonnull
	private ResolvedPath getDescriptorFilename(ResolvedPath theStoragePath, IIdType theResourceId, String theId) {
		return this.getStorageFilename(theStoragePath, theResourceId, theId, ".json");
	}

	private static class ResolvedPath {
		private final String basePath;
		private final String relativePath;
		private final String fullPath;

		public ResolvedPath(String basePath, String path) {
			this.basePath = basePath;
			this.relativePath = path;
			this.fullPath = buildFullPath();
		}

		public ResolvedPath(ResolvedPath resolvedPath, String subPath) {
			this(resolvedPath.getFullPath(), subPath);
		}

		private String buildFullPath() {
			if (StringUtils.isBlank(basePath)) {
				if (StringUtils.isBlank(relativePath)) throw translateError(new Exception("Name is empty"));
				return relativePath;
			}
			if (StringUtils.isBlank(relativePath)) return basePath;
			return String.format("%s/%s", basePath, relativePath);
		}

		public String getFullPath() {
			return fullPath;
		}
	}
}

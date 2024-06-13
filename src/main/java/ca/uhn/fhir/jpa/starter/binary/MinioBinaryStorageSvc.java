package ca.uhn.fhir.jpa.starter.binary;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.jpa.binary.api.StoredDetails;
import ca.uhn.fhir.jpa.binary.svc.BaseBinaryStorageSvcImpl;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Date;

public class MinioBinaryStorageSvc extends BaseBinaryStorageSvcImpl {
	private static final Logger log = LoggerFactory.getLogger(MinioBinaryStorageSvc.class);
	private final String minioBaseUrl;
	private final String accessKey;
	private final String secretKey;
	private final String bucketName;
	private final MinioClient client;
	private boolean bucketExist = false;
	private final String basePath;
	private final ObjectMapper objectMapper;

	public MinioBinaryStorageSvc(String minioBaseUrl, String accessKey, String secretKey, String bucketName, String basePath, ObjectMapper objectMapper) {
		assert minioBaseUrl != null;
		assert accessKey != null;
		assert secretKey != null;
		assert bucketName != null;
		this.minioBaseUrl = minioBaseUrl;
		this.accessKey = accessKey;
		this.secretKey = secretKey;
		this.bucketName = bucketName;
		this.basePath = basePath == null ? "" : basePath;
		this.objectMapper = objectMapper;
		this.client = buildClient();
	}

	private MinioClient buildClient() {
		return MinioClient.builder()
			.endpoint(minioBaseUrl)
			.credentials(accessKey, secretKey)
			.build();
	}

	public boolean isValidBinaryContentId(String theNewBinaryContentId) {
		return !StringUtils.containsAny(theNewBinaryContentId, '\\', '/', '|', '.');
	}

	@NotNull
	@Override
	public StoredDetails storeBinaryContent(IIdType theResourceId, String theBlobIdOrNull, String theContentType, InputStream inputStream, RequestDetails requestDetails) throws IOException {
		ensureBucketExist();
		String id = super.provideIdForNewBinaryContent(theBlobIdOrNull, null, requestDetails, theContentType);
		ResolvedPath storagePath = getStoragePath(id);
		ResolvedPath storageFilename = getStorageFilename(storagePath, theResourceId, id);

		log.info("Writing to file: {}", storageFilename.getFullPath());
		var countingInputStream = this.createCountingInputStream(inputStream);
		//noinspection UnstableApiUsage
		var hashingInputStream = this.createHashingInputStream(countingInputStream);
		write(storageFilename, hashingInputStream);

		long count = countingInputStream.getByteCount();
		//noinspection UnstableApiUsage
		StoredDetails details = new StoredDetails(id, count, theContentType, hashingInputStream, new Date());
		ResolvedPath descriptorFile = this.getDescriptorFilename(storagePath, theResourceId, id);
		var storedDetailJson = objectMapper.writeValueAsString(details);

		log.info("Writing to file: {}", descriptorFile.getFullPath());
		write(descriptorFile, new ByteArrayInputStream(storedDetailJson.getBytes()));

		log.info("Stored binary blob with {} bytes and ContentType {} for resource {}", count, theContentType, theResourceId);
		return details;
	}

	@Override
	public StoredDetails fetchBinaryContentDetails(IIdType theResourceId, String theBlobId) throws IOException {
		var storagePath = this.getStoragePath(theBlobId);
		if (storagePath == null) return null;

		var descriptorFile = getDescriptorFilename(storagePath, theResourceId, theBlobId);
		if (!objectExist(descriptorFile)) return null;

		var descriptorFileStream = read(descriptorFile);
		if (descriptorFileStream == null) throw translateError(new Exception("Unable to load"));

		try (var reader = new InputStreamReader(descriptorFileStream, Charsets.UTF_8)) {
			return objectMapper.readValue(reader, StoredDetails.class);
		} catch (Exception e) {
			throw translateError(new Exception("Unable to load"));
		}
	}

	@Override
	public boolean writeBinaryContent(IIdType theResourceId, String theBlobId, OutputStream theOutputStream) throws IOException {
		var inputStream = this.getInputStream(theResourceId, theBlobId);
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
		ResolvedPath storagePath = this.getStoragePath(theBlobId);
		if (storagePath == null) return;

		ResolvedPath storageFile = this.getStorageFilename(storagePath, theResourceId, theBlobId);
		if (objectExist(storageFile)) remove(storageFile);

		var descriptorFile = this.getDescriptorFilename(storagePath, theResourceId, theBlobId);
		if (objectExist(descriptorFile)) remove(descriptorFile);
	}

	@Override
	public byte[] fetchBinaryContent(IIdType theResourceId, String theBlobId) throws IOException {
		var details = fetchBinaryContentDetails(theResourceId, theBlobId);
		try (InputStream inputStream = getInputStream(theResourceId, theBlobId)) {
			if (inputStream != null) {
				return IOUtils.toByteArray(inputStream, details.getBytes());
			}
		}

		throw new ResourceNotFoundException(
			Msg.code(1327) + "Unknown blob ID: " + theBlobId + " for resource ID " + theResourceId);
	}

	@Nullable
	private InputStream getInputStream(IIdType theResourceId, String theBlobId) throws FileNotFoundException {
		ResolvedPath storagePath = this.getStoragePath(theBlobId);
		if (storagePath == null) return null;
		var file = this.getStorageFilename(storagePath, theResourceId, theBlobId);
		return objectExist(file) ? read(file) : null;
	}

	private static BaseServerResponseException translateError(Exception e) {
		log.error("Unable to ensure bucket exist", e);
		var outcome = new OperationOutcome();
		outcome.addIssue()
			.setSeverity(OperationOutcome.IssueSeverity.FATAL)
			.setCode(OperationOutcome.IssueType.UNKNOWN);
		return new InternalErrorException("Unable to write", outcome);
	}

	/**
	 * Check if the given bucket exist. If it is missing, it will create a new bucket
	 */
	private void ensureBucketExist() {
		if (bucketExist) return;

		try {
			var bucketExistsArgs = BucketExistsArgs.builder().bucket(bucketName).build();
			var found = client.bucketExists(bucketExistsArgs);
			if (!found) {
				log.info("Create a new bucket since existing bucket is missing");
				var makeBucketArgs = MakeBucketArgs.builder().bucket(bucketName).build();
				client.makeBucket(makeBucketArgs);
			}
			bucketExist = true;
		} catch (Exception e) {
			throw translateError(e);
		}
	}

	private Boolean objectExist(ResolvedPath path) {
		try {
			var statArgs = StatObjectArgs.builder()
				.bucket(bucketName)
				.object(path.getFullPath()).build();
			client.statObject(statArgs);
			return true;
		} catch (ErrorResponseException e) {
			return false;
		} catch (Exception e) {
			throw translateError(e);
		}
	}

	private InputStream read(ResolvedPath path) {
		try {
			var readArgs = GetObjectArgs.builder()
				.bucket(bucketName)
				.object(path.getFullPath()).build();
			return client.getObject(readArgs);
		} catch (ErrorResponseException e) {
			return null;
		} catch (Exception e) {
			throw translateError(e);
		}
	}

	private void write(ResolvedPath resolvedPath, InputStream stream) {
		// 64 MB Part size is ideal for multipart upload
		long partSize = 64 * 1024 * 1024;

		var args = PutObjectArgs.builder()
			.bucket(bucketName)
			.object(resolvedPath.getFullPath())
			.stream(stream, -1, partSize)
			.build();
		try {
			client.putObject(args);
		} catch (Exception e) {
			throw translateError(e);
		}
	}

	private void remove(ResolvedPath resolvedPath) {
		var args = RemoveObjectArgs.builder()
			.bucket(bucketName)
			.object(resolvedPath.getFullPath())
			.build();
		try {
			client.removeObject(args);
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

		public ResolvedPath(String basePath) {
			this(basePath, null);
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

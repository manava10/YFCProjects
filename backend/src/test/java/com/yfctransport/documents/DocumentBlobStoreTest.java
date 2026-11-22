package com.yfctransport.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

class DocumentBlobStoreTest {

  @Test
  void localBlobStoreWritesReadsDeletesAndBlocksTraversal(@TempDir Path dir) {
    var store = new LocalDocumentBlobStore(dir);
    UUID id = UUID.randomUUID();
    byte[] payload = "%PDF-1.4\n%local-blob\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    String key = store.put(id, "TRIP", "slip.pdf", "application/pdf", payload);
    assertThat(key).isEqualTo("trip/" + id);
    assertThat(store.get(key)).isEqualTo(payload);

    assertThatThrownBy(() -> store.get("../../etc/passwd"))
        .isInstanceOf(ResponseStatusException.class);

    store.deleteQuietly(key);
    assertThatThrownBy(() -> store.get(key)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void s3BlobStoreSignsRequestsWithAwsSigV4AndRoundTripsBytes() {
    Map<String, byte[]> bucketObjects = new ConcurrentHashMap<>();
    AtomicReference<String> capturedAuth = new AtomicReference<>();
    AtomicReference<String> capturedSha = new AtomicReference<>();
    AtomicReference<String> capturedDate = new AtomicReference<>();
    AtomicReference<String> capturedUri = new AtomicReference<>();

    S3DocumentBlobStore.Transport mockTransport =
        (request, body) -> {
          String path = request.uri().getPath();
          capturedUri.set(request.uri().toString());
          capturedAuth.set(request.headers().firstValue("Authorization").orElse(""));
          capturedSha.set(request.headers().firstValue("x-amz-content-sha256").orElse(""));
          capturedDate.set(request.headers().firstValue("x-amz-date").orElse(""));
          return switch (request.method()) {
            case "PUT" -> {
              bucketObjects.put(path, body);
              yield new S3DocumentBlobStore.Response(200, new byte[0]);
            }
            case "GET" -> {
              byte[] found = bucketObjects.get(path);
              yield found == null
                  ? new S3DocumentBlobStore.Response(404, new byte[0])
                  : new S3DocumentBlobStore.Response(200, found);
            }
            case "DELETE" -> {
              bucketObjects.remove(path);
              yield new S3DocumentBlobStore.Response(204, new byte[0]);
            }
            default -> new S3DocumentBlobStore.Response(405, new byte[0]);
          };
        };

    var creds =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(
                "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"));
    var store =
        new S3DocumentBlobStore("yfc-docs-prod", "ap-south-2", "pod", "", creds, mockTransport);

    UUID id = UUID.randomUUID();
    byte[] payload = "%PDF-1.4\n%s3-sigv4-blob\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    String key = store.put(id, "TRIP", "weighbridge.pdf", "application/pdf", payload);
    assertThat(key).isEqualTo("pod/trip/" + id);
    assertThat(capturedUri.get())
        .isEqualTo("https://yfc-docs-prod.s3.ap-south-2.amazonaws.com/pod/trip/" + id);
    assertThat(capturedAuth.get()).startsWith("AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/");
    assertThat(capturedAuth.get()).contains("/ap-south-2/s3/aws4_request");
    assertThat(capturedAuth.get()).contains("SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date");
    assertThat(capturedSha.get()).hasSize(64);
    assertThat(capturedDate.get()).matches("\\d{8}T\\d{6}Z");

    assertThat(store.get(key)).isEqualTo(payload);

    store.deleteQuietly(key);
    assertThatThrownBy(() -> store.get(key)).isInstanceOf(ResponseStatusException.class);
  }
}

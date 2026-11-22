package com.yfctransport.documents;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

/**
 * S3-compatible object store using native AWS SigV4 (AWS4-HMAC-SHA256) signing and JDK HttpClient.
 * Works with AWS S3 (IAM roles / SSO / env credentials), Cloudflare R2, MinIO, and custom endpoints.
 */
class S3DocumentBlobStore implements DocumentBlobStore {
  private static final DateTimeFormatter AMZ_DATE =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter DATE_STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

  interface Transport {
    Response send(HttpRequest request, byte[] requestBody) throws Exception;
  }

  record Response(int statusCode, byte[] body) {}

  private final String bucket;
  private final String region;
  private final String prefix;
  private final String customEndpoint;
  private final AwsCredentialsProvider credentialsProvider;
  private final Transport transport;

  S3DocumentBlobStore(String bucket, String region, String prefix, String customEndpoint) {
    this(
        bucket,
        region,
        prefix,
        customEndpoint,
        DefaultCredentialsProvider.builder().build(),
        defaultTransport());
  }

  S3DocumentBlobStore(
      String bucket,
      String region,
      String prefix,
      String customEndpoint,
      AwsCredentialsProvider credentialsProvider,
      Transport transport) {
    this.bucket = bucket == null ? "" : bucket.strip();
    this.region = region == null || region.isBlank() ? "ap-south-2" : region.strip();
    this.prefix = trimSlashes(prefix == null ? "" : prefix.strip());
    this.customEndpoint = customEndpoint == null ? "" : customEndpoint.strip().replaceAll("/+$", "");
    this.credentialsProvider = credentialsProvider;
    this.transport = transport;
  }

  private static Transport defaultTransport() {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    return (request, body) -> {
      HttpResponse<byte[]> res = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
      return new Response(res.statusCode(), res.body());
    };
  }

  boolean isConfigured() {
    return !bucket.isBlank();
  }

  @Override
  public String backendName() {
    return "S3";
  }

  @Override
  public String put(UUID id, String targetType, String filename, String mediaType, byte[] bytes) {
    requireConfigured();
    String key =
        (prefix.isBlank() ? "" : prefix + "/")
            + targetType.toLowerCase(Locale.ROOT)
            + "/"
            + id;
    URI uri = objectUri(key);
    try {
      HttpRequest req = signedRequest("PUT", uri, mediaType, bytes);
      Response res = transport.send(req, bytes);
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "S3 upload failed with HTTP " + res.statusCode());
      }
      return key;
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "S3 upload failed", ex);
    }
  }

  @Override
  public byte[] get(String storageKey) {
    requireConfigured();
    URI uri = objectUri(storageKey);
    try {
      HttpRequest req = signedRequest("GET", uri, null, new byte[0]);
      Response res = transport.send(req, new byte[0]);
      if (res.statusCode() == 404) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored S3 document not found");
      }
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "S3 download failed with HTTP " + res.statusCode());
      }
      return res.body();
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "S3 download failed", ex);
    }
  }

  @Override
  public void deleteQuietly(String storageKey) {
    if (!isConfigured() || storageKey == null || storageKey.isBlank()) return;
    try {
      HttpRequest req = signedRequest("DELETE", objectUri(storageKey), null, new byte[0]);
      transport.send(req, new byte[0]);
    } catch (Exception ignored) {
    }
  }

  private void requireConfigured() {
    if (!isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "S3 document storage is selected but yfc.documents.s3.bucket is not configured");
    }
  }

  private URI objectUri(String key) {
    String encodedKey = encodePath(key);
    if (!customEndpoint.isBlank()) {
      return URI.create(customEndpoint + "/" + encodeSegment(bucket) + "/" + encodedKey);
    }
    return URI.create("https://" + bucket + ".s3." + region + ".amazonaws.com/" + encodedKey);
  }

  private HttpRequest signedRequest(String method, URI uri, String contentType, byte[] payload)
      throws Exception {
    AwsCredentials creds = credentialsProvider.resolveCredentials();
    Instant now = Instant.now();
    String amzDate = AMZ_DATE.format(now);
    String dateStamp = DATE_STAMP.format(now);
    String payloadHash = sha256Hex(payload);
    String host = uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();

    TreeMap<String, String> headers = new TreeMap<>();
    headers.put("host", host);
    headers.put("x-amz-content-sha256", payloadHash);
    headers.put("x-amz-date", amzDate);
    if (contentType != null && !contentType.isBlank()) headers.put("content-type", contentType);
    if (creds instanceof AwsSessionCredentials sc && sc.sessionToken() != null) {
      headers.put("x-amz-security-token", sc.sessionToken());
    }

    StringBuilder canonicalHeaders = new StringBuilder();
    StringBuilder signedHeaders = new StringBuilder();
    for (var entry : headers.entrySet()) {
      canonicalHeaders.append(entry.getKey()).append(":").append(entry.getValue().strip()).append("\n");
      if (!signedHeaders.isEmpty()) signedHeaders.append(";");
      signedHeaders.append(entry.getKey());
    }

    String canonicalUri = uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
    String canonicalRequest =
        method
            + "\n"
            + canonicalUri
            + "\n\n"
            + canonicalHeaders
            + "\n"
            + signedHeaders
            + "\n"
            + payloadHash;

    String scope = dateStamp + "/" + region + "/s3/aws4_request";
    String stringToSign =
        "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

    byte[] signingKey = deriveSigningKey(creds.secretAccessKey(), dateStamp, region, "s3");
    String signature = HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign));

    String authorization =
        "AWS4-HMAC-SHA256 Credential="
            + creds.accessKeyId()
            + "/"
            + scope
            + ", SignedHeaders="
            + signedHeaders
            + ", Signature="
            + signature;

    HttpRequest.Builder builder =
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("x-amz-content-sha256", payloadHash)
            .header("x-amz-date", amzDate)
            .header("Authorization", authorization);
    if (contentType != null && !contentType.isBlank()) builder.header("Content-Type", contentType);
    if (creds instanceof AwsSessionCredentials sc && sc.sessionToken() != null) {
      builder.header("x-amz-security-token", sc.sessionToken());
    }

    return switch (method) {
      case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
      case "DELETE" -> builder.DELETE().build();
      default -> builder.GET().build();
    };
  }

  private static byte[] deriveSigningKey(String secret, String dateStamp, String region, String service)
      throws Exception {
    byte[] kDate = hmacSha256(("AWS4" + secret).getBytes(StandardCharsets.UTF_8), dateStamp);
    byte[] kRegion = hmacSha256(kDate, region);
    byte[] kService = hmacSha256(kRegion, service);
    return hmacSha256(kService, "aws4_request");
  }

  private static byte[] hmacSha256(byte[] key, String data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
  }

  private static String sha256Hex(byte[] data) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
  }

  private static String trimSlashes(String s) {
    return s.replaceAll("^/+", "").replaceAll("/+$", "");
  }

  private static String encodePath(String path) {
    String[] parts = trimSlashes(path).split("/");
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) out.append("/");
      out.append(encodeSegment(parts[i]));
    }
    return out.toString();
  }

  private static String encodeSegment(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8)
        .replace("+", "%20")
        .replace("*", "%2A")
        .replace("%7E", "~");
  }
}

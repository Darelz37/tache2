package cm.proclama.service;

import cm.proclama.config.AppProps;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/** Contrat S3 minimal : bucket, put, get (flux), head, delete. Adressage par chemin. */
@Service
public class S3Service {
  private final S3Client s3;
  private final String bucket;
  private volatile boolean bucketOk = false;

  public S3Service(AppProps p) {
    this.bucket = p.s3Bucket();
    this.s3 = S3Client.builder()
        .endpointOverride(URI.create(p.s3Endpoint()))
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(p.s3AccessKey(), p.s3SecretKey())))
        .forcePathStyle(true)
        .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(30)))
        .build();
  }

  public synchronized void ensureBucket() {
    if (bucketOk) return;
    try {
      s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
    } catch (S3Exception e) {
      if (e.statusCode() != 404) throw e;
      s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
    }
    bucketOk = true;
  }

  public void put(String key, InputStream in, long length, String contentType) {
    ensureBucket();
    s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
        RequestBody.fromInputStream(in, length));
  }

  public void putBytes(String key, byte[] data, String contentType) {
    ensureBucket();
    s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
        RequestBody.fromBytes(data));
  }

  public ResponseInputStream<GetObjectResponse> getStream(String key) {
    return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
  }

  public byte[] getBytes(String key) throws IOException {
    try (InputStream in = getStream(key)) {
      return in.readAllBytes();
    }
  }

  public boolean exists(String key) {
    ensureBucket();
    try {
      s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    } catch (S3Exception e) {
      if (e.statusCode() == 404) return false;
      throw e;
    }
  }

  public void delete(String key) {
    s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
  }

  public void ping() {
    ensureBucket();
  }
}

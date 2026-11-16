package cm.proclama.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuration validée au démarrage : une variable obligatoire absente fait échouer le démarrage. */
@ConfigurationProperties(prefix = "app")
@Validated
public record AppProps(
    String mode,
    @NotBlank String databaseUrl,
    int dbPoolSize,
    @NotBlank String s3Endpoint,
    @NotBlank String s3Bucket,
    @NotBlank String s3AccessKey,
    @NotBlank String s3SecretKey,
    @NotBlank String jwtSecret,
    String appVersion,
    String instanceName,
    boolean enableDebug,
    int rateLimitPerMin,
    int workerIntervalSeconds,
    String bootstrapAdminEmail,
    String bootstrapAdminPassword) {}

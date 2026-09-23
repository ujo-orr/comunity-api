package org.example.communityapi.attachment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "file.storage-type", havingValue = "s3")
public class S3StorageConfig {
    @Bean(destroyMethod = "close")
    S3Client s3Client(@Value("${file.s3.region}") String region) {
        Assert.hasText(region, "AWS_REGION must not be blank");
        // AWS SDK 기본 인증 설정으로 EC2 IAM Role 사용
        return S3Client.builder()
                .region(Region.of(region))
                .overrideConfiguration(config -> config
                        .apiCallAttemptTimeout(Duration.ofSeconds(20))
                        .apiCallTimeout(Duration.ofSeconds(60)))
                .build();
    }
}

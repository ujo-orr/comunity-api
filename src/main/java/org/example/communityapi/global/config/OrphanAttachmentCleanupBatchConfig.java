package org.example.communityapi.global.config;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.attachment.OrphanAttachmentCleanupService;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
@RequiredArgsConstructor
public class OrphanAttachmentCleanupBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final OrphanAttachmentCleanupService orphanAttachmentCleanupService;

    @Bean
    Job orphanAttachmentCleanupJob(@Qualifier("orphanAttachmentCleanupStep") Step orphanAttachmentCleanupStep) {
        return new JobBuilder("orphanAttachmentCleanupJob", jobRepository)
                .start(orphanAttachmentCleanupStep)
                .build();
    }

    @Bean
    Step orphanAttachmentCleanupStep() {
        return new StepBuilder("orphanAttachmentCleanupStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    orphanAttachmentCleanupService.cleanup();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}

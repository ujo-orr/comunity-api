package org.example.comunityapi.global.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class WithdrawalBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job cleanupJob() {
        return new JobBuilder("cleanupWithdrawalJob", jobRepository)
                .start(cleanupStep())
                .build();
    }

    @Bean
    public Step cleanupStep() {
        return new StepBuilder("cleanupWithdrawalStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    // 유예 기간 만료 데이터 삭제 또는 이관 로직 수행
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}

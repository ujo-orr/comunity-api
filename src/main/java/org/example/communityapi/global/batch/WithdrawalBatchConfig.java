package org.example.communityapi.global.batch;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.member.MemberWithdrawalRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;

@Configuration
@RequiredArgsConstructor
public class WithdrawalBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final MemberWithdrawalRepository memberWithdrawalRepository;

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
                    memberWithdrawalRepository.deleteByExpireAtBefore(LocalDateTime.now());

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}

package org.example.communityapi.global.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class OrphanAttachmentCleanupScheduler {

    private final JobLauncher jobLauncher;
    private final Job orphanAttachmentCleanupJob;

    // 기존 탈퇴 정리(04:00)와 겹치지 않게 매일 새벽 05:00에 실행한다.
    @Scheduled(cron = "0 0 5 * * *")
    public void runOrphanAttachmentCleanupJob() {
        try {
            jobLauncher.run(
                    orphanAttachmentCleanupJob,
                    new JobParametersBuilder()
                            .addLong("time", System.currentTimeMillis())
                            .toJobParameters()
            );
        } catch (JobExecutionAlreadyRunningException
                 | JobRestartException
                 | JobInstanceAlreadyCompleteException
                 | JobParametersInvalidException e) {

            log.error("고아 첨부파일 정리 배치 실행에 실패했습니다.", e);
        }
    }
}

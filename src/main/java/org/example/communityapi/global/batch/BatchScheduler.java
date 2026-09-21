package org.example.communityapi.global.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job cleanupJob;

    // 매일 새벽 4시에 보관 기간이 지난 탈퇴 기록 삭제
    @Scheduled(cron = "0 0 4 * * *")
    public void runCleanupJob() throws Exception {
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis()) // Batch 재실행을 위한 시간 파라미터 설정
                .toJobParameters();

        jobLauncher.run(cleanupJob, jobParameters);
    }
}

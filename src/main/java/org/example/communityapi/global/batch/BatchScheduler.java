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
                .addLong("time", System.currentTimeMillis()) // 같은 작업도 매번 실행되도록 시간 추가
                .toJobParameters();

        jobLauncher.run(cleanupJob, jobParameters);
    }
}

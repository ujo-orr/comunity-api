package org.example.comunityapi.global.batch;

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

    // 매일 04시에 배치 실행
    @Scheduled(cron = "0 0 4 * * *")
    public void runCleanupJob() throws Exception {
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis()) // 매번 다른 파라미터를 넘겨주어야 실행됨
                .toJobParameters();

        jobLauncher.run(cleanupJob, jobParameters);
    }
}

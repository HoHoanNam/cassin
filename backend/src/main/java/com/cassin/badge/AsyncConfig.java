package com.cassin.badge;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bật @Async và cấp một thread pool RIÊNG cho việc xét huy hiệu (không tranh thread với việc khác).
 *
 * cassin.badge.async = false (application-it.yml): chạy ngay trên thread gọi. Test tích hợp nộp bài xong
 * là kiểm được huy hiệu luôn, không phải chờ / poll.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
  public static final String BADGE_EXECUTOR = "badgeExecutor";

  @Bean(name = BADGE_EXECUTOR)
  Executor badgeExecutor(@Value("${cassin.badge.async:true}") boolean async) {
    if (!async) {
      return new SyncTaskExecutor();
    }
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("badge-");
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    // Hàng đợi đầy (cực hiếm) thì bỏ sự kiện + log, không chặn request. Lần học sau sẽ xét lại.
    executor.setQueueCapacity(500);
    executor.setRejectedExecutionHandler((task, pool) ->
        org.slf4j.LoggerFactory.getLogger(AsyncConfig.class).warn("Hàng đợi huy hiệu đầy, bỏ qua một sự kiện"));
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(10);
    executor.initialize();
    return executor;
  }
}

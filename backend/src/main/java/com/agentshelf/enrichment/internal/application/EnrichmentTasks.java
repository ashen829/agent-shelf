package com.agentshelf.enrichment.internal.application;

import com.github.kagkarlsson.scheduler.task.CompletionHandler;
import com.github.kagkarlsson.scheduler.task.FailureHandler;
import com.github.kagkarlsson.scheduler.task.helper.CustomTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The background task for AI cleanup. One task instance per job (task data: the job's ID) that
 * reschedules itself until the job is finished: while Claude works on the batch it checks once a
 * minute.
 */
@Configuration(proxyBeanMethods = false)
class EnrichmentTasks {

  static final String ENRICHMENT_JOB = "enrichment-job";

  @Bean
  CustomTask<String> enrichmentJobTask(EnrichmentRunner runner) {
    return Tasks.custom(ENRICHMENT_JOB, String.class)
        .onFailure(
            new FailureHandler.MaxRetriesFailureHandler<>(
                EnrichmentRunner.MAX_ATTEMPTS,
                new FailureHandler.ExponentialBackoffFailureHandler<>(Duration.ofSeconds(30))))
        .execute((instance, context) -> {
          Instant next =
              runner.run(
                  UUID.fromString(instance.getData()), context.getExecution().consecutiveFailures);
          if (next == null) {
            return new CompletionHandler.OnCompleteRemove<>();
          }
          return (complete, operations) -> operations.reschedule(complete, next);
        });
  }
}

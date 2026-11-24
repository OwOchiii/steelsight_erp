package orochi.steelsight.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled, so JobScheduler ticks every minute: Zalo deliveries, check-out hours and the
 * jobs of SRS 3.2.38. Without it the tick ran only once at startup and notices waited for a restart.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}

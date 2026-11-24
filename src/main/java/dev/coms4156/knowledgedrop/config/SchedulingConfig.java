package dev.coms4156.knowledgedrop.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} methods, such as the stale chunk cleanup job. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}

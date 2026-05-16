package com.satyajit.upioffline.config;

/*
 * App-level Spring configuration.
 * @EnableScheduling activates the @Scheduled eviction job in IdempotencyServiceImpl.
 */

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AppConfig {
}
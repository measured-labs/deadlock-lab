package com.experiment.deadlocklab.global.config;

import com.experiment.deadlocklab.global.logger.ConsoleLogger;
import com.experiment.deadlocklab.global.metrics.MetricsCollector;
import com.experiment.deadlocklab.global.visualizer.ThreadVisualizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Bean 설정
 */
@Configuration
public class BeanConfig {

    @Bean
    public MetricsCollector metricsCollector() {
        return new MetricsCollector();
    }

    @Bean
    public ConsoleLogger consoleLogger() {
        return new ConsoleLogger("DeadlockLab");
    }

    @Bean
    public ThreadVisualizer threadVisualizer() {
        return new ThreadVisualizer();
    }
}

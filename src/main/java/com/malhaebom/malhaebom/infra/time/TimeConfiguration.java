package com.malhaebom.malhaebom.infra.time;

import java.time.Clock;
import java.util.function.LongSupplier;

import io.github.bucket4j.TimeMeter;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	TimeMeter rateLimitTimeMeter() {
		return TimeMeter.SYSTEM_NANOTIME;
	}

	@Bean
	@Qualifier("answerAssessmentNanoTime")
	LongSupplier answerAssessmentNanoTime() {
		return System::nanoTime;
	}
}

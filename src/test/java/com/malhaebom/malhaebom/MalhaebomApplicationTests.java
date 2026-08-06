package com.malhaebom.malhaebom;

import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

@SpringBootTest
@ActiveProfiles("test")
@Import(MalhaebomApplicationTests.SpeechTestConfiguration.class)
class MalhaebomApplicationTests {

	@Test
	void contextLoads() {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SpeechTestConfiguration {

		@Bean
		SpeechTranscriber speechTranscriber() {
			return mock(SpeechTranscriber.class);
		}
	}

}

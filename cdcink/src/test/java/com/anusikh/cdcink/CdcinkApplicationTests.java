package com.anusikh.cdcink;

import com.anusikh.cdcink.repository.CdcDocumentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
		"spring.data.elasticsearch.repositories.enabled=false",
		"spring.main.allow-bean-definition-overriding=true"
})
@Import(CdcinkApplicationTests.TestConfig.class)
class CdcinkApplicationTests {

	@Test
	void contextLoads() {
	}

	@TestConfiguration
	static class TestConfig {
		@Bean
		CdcDocumentRepository cdcDocumentRepository() {
			return Mockito.mock(CdcDocumentRepository.class);
		}
	}

}

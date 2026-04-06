package com.anusikh.cdcink.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@ConditionalOnProperty(
        name = "spring.data.elasticsearch.repositories.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableElasticsearchRepositories(basePackages = "com.anusikh.cdcink.repository")
public class ElasticsearchConfig {

}

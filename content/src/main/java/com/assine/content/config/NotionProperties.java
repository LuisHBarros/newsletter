package com.assine.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "content.notion")
public class NotionProperties {
    private String apiBaseUrl = "https://api.notion.com/v1";
    private String apiToken;
    private String webhookSecret;
    private Duration timeout = Duration.ofSeconds(10);
    private String version = "2022-06-28";
}

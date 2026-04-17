package com.assine.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "content")
public class ContentProperties {

    private Storage storage = new Storage();
    private Scheduler scheduler = new Scheduler();
    private Outbox outbox = new Outbox();

    @Data
    public static class Storage {
        private String bucket;
        private String keyPrefix = "content";
    }

    @Data
    public static class Scheduler {
        private ToggleCron publishScheduled = new ToggleCron(true, "0 * * * * *");
        private ToggleCron reconcileNotion = new ToggleCron(true, "0 */15 * * * *");
    }

    @Data
    public static class Outbox {
        private ToggleCron relay = new ToggleCron(true, "*/5 * * * * *");
    }

    @Data
    public static class ToggleCron {
        private boolean enabled;
        private String cron;

        public ToggleCron() {}
        public ToggleCron(boolean enabled, String cron) {
            this.enabled = enabled;
            this.cron = cron;
        }
    }
}

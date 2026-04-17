package com.assine.content;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ContentApplicationTests {

    @Test
    void contextLoads() {
        // Verifies wiring of all beans in the 'test' profile (H2 + in-memory).
    }
}

package online.misterpilot.platform;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test — requires a running PostgreSQL database.
 * Disabled by default; enable when DB is available.
 */
@SpringBootTest
@Disabled("Requires running PostgreSQL database. Run manually with DB available.")
class PlatformApplicationTests {

    @Test
    void contextLoads() {
    }

}

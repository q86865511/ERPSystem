package com.erp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Context smoke test. Named {@code *IT} because it boots a Testcontainers PostgreSQL: it belongs to the
 * Failsafe (`verify`) phase, which keeps `mvn test` runnable without a Docker daemon.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ErpApplicationIT {

	@Test
	void contextLoads() {
	}

}

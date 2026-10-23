package com.relay.relay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "relay.worker.enabled=false")
class RelayApplicationTests {

	@Test
	void contextLoads() {
	}

}

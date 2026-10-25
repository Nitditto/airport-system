package com.nitditto;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"razorpay.api.key=context-loads-key",
		"razorpay.api.secret=context-loads-secret"
})
class PaymentServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}

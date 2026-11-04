package com.nitditto;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"razorpay.api.key=context-loads-key",
		"razorpay.api.secret=context-loads-secret",
		"vnpay.tmn-code=CONTEXTLOADS",
		"vnpay.hash-secret=context-loads-hash-secret"
})
class PaymentServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}

package ch.zhaw.rpa.robowebhookhandler.webhookhandler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class WebhookhandlerApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebhookhandlerApplication.class, args);
	}

}

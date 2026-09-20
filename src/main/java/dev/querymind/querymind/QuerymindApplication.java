package dev.querymind.querymind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * We turn off UserDetailsServiceAutoConfiguration because we do our own
 * login with JWT tokens. Without this, Spring Boot would create a default
 * user and print a random password at startup, which we don't use.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class QuerymindApplication {

	public static void main(String[] args) {
		SpringApplication.run(QuerymindApplication.class, args);
	}

}

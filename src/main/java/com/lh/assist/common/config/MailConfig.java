package com.lh.assist.common.config;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailConfig {

	@Value("${spring.mail.host:}")
	private String host;

	@Value("${spring.mail.port:465}")
	private int port;

	@Value("${spring.mail.username:}")
	private String username;

	@Value("${spring.mail.password:}")
	private String password;

	@Bean
	public JavaMailSender javaMailSender() {
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(host);
		sender.setPort(port);
		sender.setUsername(username);
		sender.setPassword(password);
		sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

		Properties props = sender.getJavaMailProperties();
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.ssl.enable", "true");
		props.put("mail.smtp.ssl.trust", host);
		return sender;
	}
}
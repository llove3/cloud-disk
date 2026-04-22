package com.example.clouddisk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.awt.*;
import java.net.URI;

@SpringBootApplication
public class CloudDiskApplication {

	private final Environment environment;

	public CloudDiskApplication(Environment environment) {
		this.environment = environment;
	}

	public static void main(String[] args) {
		SpringApplication.run(CloudDiskApplication.class, args);
	}



	@EventListener(ApplicationReadyEvent.class)
	public void openBrowserAfterStartup() {
		String port = environment.getProperty("server.port", "8080");
		String url = "http://localhost:" + port + "/login";

		try {
			Thread.sleep(800);
		} catch (InterruptedException ignored) {
		}

		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			try {
				Desktop.getDesktop().browse(new URI(url));
				System.out.println("已在默认浏览器中打开：" + url);
			} catch (Exception e) {
				System.err.println("无法自动打开浏览器，请手动访问：" + url);
			}
		} else {
			System.out.println("当前系统不支持自动打开浏览器，请手动访问：" + url);
		}
	}
}
package com.rindo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(NativeHints.class)
public class RindoApplication {

	public static void main(String[] args) {
		SpringApplication.run(RindoApplication.class, args);
	}

}

package com.rindo;

import org.springframework.boot.SpringApplication;

public class TestRindoApplication {

	public static void main(String[] args) {
		SpringApplication.from(RindoApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

package com.erp;

import org.springframework.boot.SpringApplication;

public class TestErpApplication {

	public static void main(String[] args) {
		SpringApplication.from(ErpApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

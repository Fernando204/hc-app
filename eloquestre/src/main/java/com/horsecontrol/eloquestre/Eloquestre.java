package com.horsecontrol.eloquestre;

import com.horsecontrol.eloquestre.services.internal.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Eloquestre {

	public static void main(String[] args) {
		SpringApplication.run(Eloquestre.class, args);
		Logger.serverStarted("http://localhost:8040");
	}


}

package com.agentshelf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/** Entry point. Business modules live in sub-packages of {@code com.agentshelf}. */
@Modulithic(sharedModules = "shared")
@SpringBootApplication
public class AgentShelfApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgentShelfApplication.class, args);
  }
}

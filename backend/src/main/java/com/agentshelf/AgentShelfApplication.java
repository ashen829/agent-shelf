package com.agentshelf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point. Business modules live in sub-packages of {@code com.agentshelf}. */
@SpringBootApplication
public class AgentShelfApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgentShelfApplication.class, args);
  }
}

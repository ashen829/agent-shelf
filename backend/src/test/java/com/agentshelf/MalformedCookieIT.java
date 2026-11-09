package com.agentshelf;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Browsers send every cookie for the host, including ones from other apps. Tomcat logs the full
 * content of a cookie it can't parse, which can include tokens or personal data. It must not.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class MalformedCookieIT {

  private static final String SECRET = "someone-elses-token-4f9a";

  @LocalServerPort private int port;

  @Test
  void malformedCookiesAreNotLogged(CapturedOutput output) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
            .header("Cookie", "g_state={\"i_l\":0,\"token\":\"" + SECRET + "\"}")
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(output.getAll()).doesNotContain(SECRET);
  }
}

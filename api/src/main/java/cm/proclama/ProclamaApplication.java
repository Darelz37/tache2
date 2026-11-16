package cm.proclama;

import cm.proclama.config.AppProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProps.class)
public class ProclamaApplication {
  public static void main(String[] args) {
    SpringApplication.run(ProclamaApplication.class, args);
  }
}

package cm.proclama.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {
  /** DATABASE_URL au format postgres://utilisateur:motdepasse@hote:5432/base. */
  @Bean
  public DataSource dataSource(AppProps p) {
    URI u = URI.create(p.databaseUrl());
    String[] ui = u.getRawUserInfo() == null ? new String[] {"", ""} : u.getRawUserInfo().split(":", 2);
    HikariConfig c = new HikariConfig();
    c.setJdbcUrl("jdbc:postgresql://" + u.getHost() + ":" + (u.getPort() < 0 ? 5432 : u.getPort()) + u.getPath());
    c.setUsername(URLDecoder.decode(ui[0], StandardCharsets.UTF_8));
    c.setPassword(ui.length > 1 ? URLDecoder.decode(ui[1], StandardCharsets.UTF_8) : "");
    c.setMaximumPoolSize(Math.max(2, p.dbPoolSize()));
    c.setPoolName("proclama");
    return new HikariDataSource(c);
  }
}

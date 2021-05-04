package org.win.cowin.vaccinealert;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("app")
@Getter
@Setter
public class AppProperties {
  private String serverPublicKeyPath;
  private String serverPrivateKeyPath;
}

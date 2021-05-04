package org.win.cowin.vaccinealert.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class Subscription {

  public SubscriptionKeys keys;
  private String endpoint;
  private Long expirationTime;

}

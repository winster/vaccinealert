package org.win.cowin.vaccinealert.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SubscriptionKeys {
  private String p256dh;
  private String auth;
}

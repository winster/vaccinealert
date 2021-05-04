package org.win.cowin.vaccinealert.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PushMessage {
  private String title;
  private String body;
}

package org.win.cowin.vaccinealert.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Center {
  private String fee_type;
  private String name;
  private List<Session> sessions;
}

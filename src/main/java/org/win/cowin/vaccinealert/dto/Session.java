package org.win.cowin.vaccinealert.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Session {
  private Integer available_capacity;
  private Integer min_age_limit;
  private List<String> slots;
}

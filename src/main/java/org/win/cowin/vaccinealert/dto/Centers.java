package org.win.cowin.vaccinealert.dto;

import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Centers implements Serializable {
  private List<Center> centers;
}

package org.win.cowin.vaccinealert.dto;

import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class States implements Serializable {
  private List<State> states;
}

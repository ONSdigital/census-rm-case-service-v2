package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;

@Data
public class PayloadDTO extends BaseDTO {

  @JsonInclude(Include.NON_NULL)
  private CollectionCase collectionCase;

  @JsonInclude(Include.NON_NULL)
  private UacDTO uac;

  @JsonInclude(Include.NON_NULL)
  private PrintCaseSelected printCaseSelected;
}

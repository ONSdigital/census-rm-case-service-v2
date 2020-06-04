package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@Service
public class FieldCaseUpdatedService {

  private final CaseRepository caseRepository;
  private final EventLogger eventLogger;
  private final CaseService caseService;
  private static final String CE_CASE_TYPE = "CE";
  private static final String FIELD_CASE_UPDATED_DESCRIPTION = "Field case update received";

  public FieldCaseUpdatedService(
      CaseRepository caseRepository, EventLogger eventLogger, CaseService caseService) {
    this.caseRepository = caseRepository;
    this.eventLogger = eventLogger;
    this.caseService = caseService;
  }

  public void processFieldCaseUpdatedEvent(
      ResponseManagementEvent responseManagementEvent, OffsetDateTime messageTimestamp) {

    CollectionCase fieldCaseUpdatedPayload =
        responseManagementEvent.getPayload().getCollectionCase();

    Case caze = caseService.getCaseAndLockIt(UUID.fromString(fieldCaseUpdatedPayload.getId()));

    if (!caze.getCaseType().equals(CE_CASE_TYPE)) {
      throw new IllegalArgumentException(
          String.format(
              "Updating expected response count for %s failed as CaseType not CE",
              caze.getCaseId().toString()));
    }

    caze.setCeExpectedCapacity(fieldCaseUpdatedPayload.getCeExpectedCapacity());

    eventLogger.logCaseEvent(
        caze,
        responseManagementEvent.getEvent().getDateTime(),
        FIELD_CASE_UPDATED_DESCRIPTION,
        EventType.FIELD_CASE_UPDATED,
        responseManagementEvent.getEvent(),
        convertObjectToJson(fieldCaseUpdatedPayload),
        messageTimestamp);

    caseService.saveCaseAndEmitCaseUpdatedEvent(
        caze, buildMetadataForFieldCaseUpdated(caze, responseManagementEvent));
  }

  private Metadata buildMetadataForFieldCaseUpdated(
      Case caze, ResponseManagementEvent fieldCaseUpdatedPayload) {
    if (fieldCaseUpdatedPayload.getPayload().getCollectionCase().getCeExpectedCapacity()
        <= caze.getCeActualResponses()) {
      return buildMetadata(
          fieldCaseUpdatedPayload.getEvent().getType(), ActionInstructionType.CANCEL);
    }
    return buildMetadata(fieldCaseUpdatedPayload.getEvent().getType(), null);
  }
}
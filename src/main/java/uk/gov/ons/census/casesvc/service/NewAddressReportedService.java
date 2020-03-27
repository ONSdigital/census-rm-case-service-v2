package uk.gov.ons.census.casesvc.service;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.utility.JsonHelper;

@Component
public class NewAddressReportedService {
  private final CaseService caseService;
  private final EventLogger eventLogger;

  public NewAddressReportedService(CaseService caseService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  public void processNewAddress(
      ResponseManagementEvent newAddressEvent, OffsetDateTime messageTimestamp) {
    CollectionCase newCollectionCase =
        newAddressEvent.getPayload().getNewAddress().getCollectionCase();
    checkManadatoryFieldsPresent(newCollectionCase);

    Case skeletonCase = createSkeletonCase(newCollectionCase);

    skeletonCase = caseService.saveNewCaseAndStampCaseRef(skeletonCase);
    caseService.emitCaseCreatedEvent(skeletonCase);

    eventLogger.logCaseEvent(
        skeletonCase,
        newAddressEvent.getEvent().getDateTime(),
        "New Address reported",
        EventType.NEW_ADDRESS_REPORTED,
        newAddressEvent.getEvent(),
        JsonHelper.convertObjectToJson(newAddressEvent.getPayload()),
        messageTimestamp);
  }

  private Case createSkeletonCase(CollectionCase collectionCase) {
    Case skeletonCase = new Case();
    skeletonCase.setSkeleton(true);
    skeletonCase.setCaseId(UUID.fromString(collectionCase.getId()));
    skeletonCase.setCollectionExerciseId(collectionCase.getCollectionExerciseId());
    skeletonCase.setAddressLine1(collectionCase.getAddress().getAddressLine1());
    skeletonCase.setAddressLine2(collectionCase.getAddress().getAddressLine2());
    skeletonCase.setAddressLine3(collectionCase.getAddress().getAddressLine3());
    skeletonCase.setTownName(collectionCase.getAddress().getTownName());
    skeletonCase.setPostcode(collectionCase.getAddress().getPostcode());
    skeletonCase.setArid(collectionCase.getAddress().getArid());
    skeletonCase.setLatitude(collectionCase.getAddress().getLatitude());
    skeletonCase.setLongitude(collectionCase.getAddress().getLongitude());
    skeletonCase.setUprn(collectionCase.getAddress().getUprn());
    skeletonCase.setRegion(collectionCase.getAddress().getRegion());
    skeletonCase.setActionPlanId(collectionCase.getActionPlanId()); // This is essential
    skeletonCase.setTreatmentCode(collectionCase.getTreatmentCode()); // This is essential
    skeletonCase.setAddressLevel(collectionCase.getAddress().getAddressLevel());
    skeletonCase.setAbpCode(collectionCase.getAddress().getApbCode());
    skeletonCase.setCaseType(collectionCase.getCaseType());
    skeletonCase.setAddressType(collectionCase.getAddress().getAddressType());
    skeletonCase.setUprn(collectionCase.getAddress().getUprn());
    skeletonCase.setEstabArid(collectionCase.getAddress().getEstabArid());
    skeletonCase.setEstabType(collectionCase.getAddress().getEstabType());
    skeletonCase.setOrganisationName(collectionCase.getAddress().getOrganisationName());
    skeletonCase.setOa(collectionCase.getOa());
    skeletonCase.setLsoa(collectionCase.getLsoa());
    skeletonCase.setMsoa(collectionCase.getMsoa());
    skeletonCase.setLad(collectionCase.getLad());
    skeletonCase.setHtcWillingness(collectionCase.getHtcWillingness());
    skeletonCase.setHtcDigital(collectionCase.getHtcDigital());
    skeletonCase.setFieldCoordinatorId(collectionCase.getFieldCoordinatorId());
    skeletonCase.setFieldOfficerId(collectionCase.getFieldOfficerId());
    skeletonCase.setCeExpectedCapacity(collectionCase.getCeExpectedCapacity());
    skeletonCase.setCeActualResponses(collectionCase.getCeActualResponses());
    skeletonCase.setHandDelivery(collectionCase.isHandDelivery());

    skeletonCase.setSurvey("CENSUS");
    skeletonCase.setRefusalReceived(false);
    skeletonCase.setReceiptReceived(false);
    skeletonCase.setAddressInvalid(false);
    skeletonCase.setUndeliveredAsAddressed(false);
    return skeletonCase;
  }

  // https://collaborate2.ons.gov.uk/confluence/display/SDC/Handle+New+Address+Reported+Events
  // Only a small number of mandatory fields to create a skeleton case
  private void checkManadatoryFieldsPresent(CollectionCase newCollectionCase) {

    if (StringUtils.isEmpty(newCollectionCase.getId())) {
      throw new RuntimeException(
          "Expected NewAddress CollectionCase Id to be a valid UUID, got: "
              + newCollectionCase.getId());
    }

    if (StringUtils.isEmpty(newCollectionCase.getAddress().getAddressType())) {
      throw new RuntimeException(
          "Unexpected newAddress CollectionCase addressType: "
              + newCollectionCase.getAddress().getAddressType());
    }

    if (StringUtils.isEmpty(newCollectionCase.getAddress().getAddressLevel())) {
      throw new RuntimeException(
          "Unexpected a valid address level in newAddress CollectionCase Address, received: "
              + newCollectionCase.getAddress().getAddressLevel());
    }

    if (StringUtils.isEmpty(newCollectionCase.getAddress().getRegion())) {
      throw new RuntimeException(
          "Invalid newAddress collectionCase Address Region: "
              + newCollectionCase.getAddress().getRegion());
    }
  }
}

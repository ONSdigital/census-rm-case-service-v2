package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.isCCSQuestionnaireType;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.FieldWorkFollowup;
import uk.gov.ons.census.casesvc.model.dto.ResponseDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@Service
public class ReceiptService {
  private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);
  public static final String QID_RECEIPTED = "QID Receipted";
  private final CaseService caseService;
  private final UacService uacService;
  private final EventLogger eventLogger;
  private final FieldworkFollowupService fieldworkFollowupService;


  public ReceiptService(CaseService caseService, UacService uacService, EventLogger eventLogger, FieldworkFollowupService fieldworkFollowupService) {
    this.caseService = caseService;
    this.uacService = uacService;
    this.eventLogger = eventLogger;
    this.fieldworkFollowupService = fieldworkFollowupService;
  }

  public void processReceipt(ResponseManagementEvent receiptEvent) {
    ResponseDTO receiptPayload = receiptEvent.getPayload().getResponse();
    UacQidLink uacQidLink = uacService.findByQid(receiptPayload.getQuestionnaireId());
    eventLogger.logUacQidEvent(
            uacQidLink,
            receiptEvent.getEvent().getDateTime(),
            QID_RECEIPTED,
            EventType.RESPONSE_RECEIVED,
            receiptEvent.getEvent(),
            convertObjectToJson(receiptPayload));

    Case caze = uacQidLink.getCaze();

    //An unreceipt doesn't un-un-active a uacQidPair
    if (!receiptPayload.getUnreceipt()) {
      uacQidLink.setActive(false);
      if (caze != null) {
        caze.setReceiptReceived(true);
      }
      //Has this uacQidLink Already been set to unreceipted, if so log it and leave.
      if (uacQidLink.isUnreceipted()) {
        return;
      }
    } else {
      uacQidLink.setUnreceipted(true);
      caze.setReceiptReceived(false);
    }


    if (isCCSQuestionnaireType(uacQidLink.getQid())) {
      uacService.saveUacQidLink(uacQidLink);
    } else {
      uacService.saveAndEmitUacUpdatedEvent(uacQidLink);
      fieldworkFollowupService.ifIUnreceiptedNeedsNewFieldWorkFolloup(caze, receiptEvent.getPayload().getResponse().getUnreceipt());

    }

    if (caze != null) {
      if (caze.isCcsCase()) {
        caseService.saveCase(caze);
      } else {
        caseService.saveAndEmitCaseUpdatedEvent(caze);
      }
    } else {
      log.with("qid", receiptPayload.getQuestionnaireId())
              .with("tx_id", receiptEvent.getEvent().getTransactionId())
              .with("channel", receiptEvent.getEvent().getChannel())
              .warn("Receipt received for unaddressed UAC/QID pair not yet linked to a case");
    }

    eventLogger.logUacQidEvent(
            uacQidLink,
            receiptEvent.getEvent().getDateTime(),
            QID_RECEIPTED,
            EventType.RESPONSE_RECEIVED,
            receiptEvent.getEvent(),
            convertObjectToJson(receiptPayload));
  }

}

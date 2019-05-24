package uk.gov.ons.census.casesvc.service;

import java.util.Date;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.utility.*;

@Component
public class UacProcessor {

  private static final String UAC_UPDATE_ROUTING_KEY = "event.uac.update";

  private final UacQidLinkRepository uacQidLinkRepository;
  private final EventRepository eventRepository;
  private final RabbitTemplate rabbitTemplate;
  private final IacDispenser iacDispenser;
  private final QidCreator qidCreator;

  @Value("${queueconfig.outbound-exchange}")
  private String outboundExchange;

  public UacProcessor(
      UacQidLinkRepository uacQidLinkRepository,
      EventRepository eventRepository,
      RabbitTemplate rabbitTemplate,
      IacDispenser iacDispenser,
      QidCreator qidCreator) {
    this.rabbitTemplate = rabbitTemplate;
    this.iacDispenser = iacDispenser;
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.eventRepository = eventRepository;
    this.qidCreator = qidCreator;
  }

  public UacQidLink saveUacQidLink(Case caze, int questionnaireType) {
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setUac(iacDispenser.getIacCode());
    uacQidLink.setCaze(caze);
    uacQidLink = uacQidLinkRepository.saveAndFlush(uacQidLink);

    // The unique number has been generated by the DB by this point, so we can use it
    String qid = qidCreator.createQid(questionnaireType, uacQidLink.getUniqueNumber());
    uacQidLink.setQid(qid);
    uacQidLinkRepository.save(uacQidLink);

    return uacQidLink;
  }

  public void logEvent(
      UacQidLink uacQidLink,
      String eventDescription,
      uk.gov.ons.census.casesvc.model.entity.EventType eventType) {
    uk.gov.ons.census.casesvc.model.entity.Event loggedEvent =
        new uk.gov.ons.census.casesvc.model.entity.Event();
    loggedEvent.setId(UUID.randomUUID());
    loggedEvent.setEventDate(new Date());
    loggedEvent.setEventDescription(eventDescription);
    loggedEvent.setUacQidLink(uacQidLink);
    loggedEvent.setEventType(eventType);
    eventRepository.save(loggedEvent);
  }

  public void emitUacUpdatedEvent(UacQidLink uacQidLink, Case caze) {
    Event event = EventHelper.createEvent(EventType.UAC_UPDATED);

    Uac uac = new Uac();
    uac.setActive(true);
    uac.setQuestionnaireId(uacQidLink.getQid());
    uac.setUacHash(Sha256Helper.hash(uacQidLink.getUac()));
    uac.setUac(uacQidLink.getUac());

    if (caze != null) {
      uac.setCaseId(caze.getCaseId().toString());
      uac.setCaseType(AddressTypeToCaseTypeMapper.mapFromAddressTypeToCaseType(caze.getAddressType()));
      uac.setCollectionExerciseId(caze.getCollectionExerciseId());
    }

    Payload payload = new Payload();
    payload.setUac(uac);
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    responseManagementEvent.setEvent(event);
    responseManagementEvent.setPayload(payload);

    rabbitTemplate.convertAndSend(
        outboundExchange, UAC_UPDATE_ROUTING_KEY, responseManagementEvent);
  }
}

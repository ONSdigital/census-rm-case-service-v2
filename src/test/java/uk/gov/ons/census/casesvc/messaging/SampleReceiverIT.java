package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.QueueSpy;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;
import uk.gov.ons.census.casesvc.utility.ObjectMapperFactory;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
public class SampleReceiverIT {
  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();
  private static final String BULK_PROCESSING_EVENT_CHANNEL = "AR"; // Address Resolution
  private static final String CREATE_BULK_CASE_SAMPLE_RECEIVED = "Create bulk case sample received";

  @Value("${queueconfig.inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.rh-case-queue}")
  private String rhCaseQueue;

  @Value("${queueconfig.rh-uac-queue}")
  private String rhUacQueue;

  @Value("${queueconfig.action-scheduler-queue}")
  private String actionSchedulerQueue;

  @Value("${queueconfig.case-updated-queue}")
  private String caseUpdatedQueueName;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(inboundQueue);
    rabbitQueueHelper.purgeQueue(rhCaseQueue);
    rabbitQueueHelper.purgeQueue(rhUacQueue);
    rabbitQueueHelper.purgeQueue(actionSchedulerQueue);
    rabbitQueueHelper.purgeQueue(caseUpdatedQueueName);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testHappyPath() throws Exception {
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue);
        QueueSpy rhUacQueueSpy = rabbitQueueHelper.listen(rhUacQueue);
        QueueSpy actionSchedulerQueueSpy = rabbitQueueHelper.listen(actionSchedulerQueue)) {
      // GIVEN
      CreateCaseSample createCaseSample = new CreateCaseSample();
      createCaseSample.setAddressType("HH");
      createCaseSample.setPostcode("ABC123");
      createCaseSample.setRegion("E12000009");
      createCaseSample.setTreatmentCode("HH_LF3R2E");
      createCaseSample.setCeExpectedCapacity(null);
      createCaseSample.setSecureEstablishment(0);
      // WHEN
      rabbitQueueHelper.sendMessage(inboundQueue, createCaseSample);

      // THEN
      ResponseManagementEvent responseManagementEvent =
          rhCaseQueueSpy.checkExpectedMessageReceived();
      assertEquals(EventTypeDTO.CASE_CREATED, responseManagementEvent.getEvent().getType());
      assertThat(responseManagementEvent.getPayload().getCollectionCase().getCreatedDateTime())
          .isNotNull();
      assertThat(responseManagementEvent.getPayload().getCollectionCase().getLastUpdated())
          .isNotNull();

      responseManagementEvent = rhUacQueueSpy.checkExpectedMessageReceived();
      assertEquals(EventTypeDTO.UAC_UPDATED, responseManagementEvent.getEvent().getType());

      List<EventTypeDTO> eventTypesSeenDTO = new LinkedList<>();
      responseManagementEvent = actionSchedulerQueueSpy.checkExpectedMessageReceived();
      eventTypesSeenDTO.add(responseManagementEvent.getEvent().getType());
      responseManagementEvent = actionSchedulerQueueSpy.checkExpectedMessageReceived();
      eventTypesSeenDTO.add(responseManagementEvent.getEvent().getType());

      assertThat(
          eventTypesSeenDTO,
          containsInAnyOrder(EventTypeDTO.CASE_CREATED, EventTypeDTO.UAC_UPDATED));

      List<Case> caseList = caseRepository.findAll();
      assertEquals(1, caseList.size());
      assertEquals("ABC123", caseList.get(0).getPostcode());
      assertNull(caseList.get(0).getCeExpectedCapacity());
      assertThat(caseList.get(0).getCreatedDateTime()).isNotNull();
      assertThat(caseList.get(0).getLastUpdated()).isNotNull();

      List<Event> eventList = eventRepository.findAll();
      assertThat(eventList.size()).isEqualTo(1);
      Event actualEvent = eventList.get(0);

      CreateCaseSample actualcreateCaseSample =
          new ObjectMapper().readValue(actualEvent.getEventPayload(), CreateCaseSample.class);

      assertThat(actualcreateCaseSample).isEqualTo(createCaseSample);
    }
  }

  @Test
  public void testBulkProcessedNewAddresses() throws Exception {
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue);
        QueueSpy rhUacQueueSpy = rabbitQueueHelper.listen(rhUacQueue);
        QueueSpy actionSchedulerQueueSpy = rabbitQueueHelper.listen(actionSchedulerQueue);
        QueueSpy fieldOutboundQueue = rabbitQueueHelper.listen(caseUpdatedQueueName)) {
      // GIVEN
      CreateCaseSample createCaseSample = new CreateCaseSample();
      createCaseSample.setAddressType("HH");
      createCaseSample.setPostcode("ABC123");
      createCaseSample.setRegion("E12000009");
      createCaseSample.setTreatmentCode("HH_LF3R2E");
      createCaseSample.setCeExpectedCapacity(null);
      createCaseSample.setSecureEstablishment(0);
      createCaseSample.setBulkProcessed(true);
      createCaseSample.setEstabType("SECRET MARS BASE TYPE 4F");
      createCaseSample.setFieldOfficerId("007");
      createCaseSample.setFieldCoordinatorId("M");
      createCaseSample.setOa("Please repurpose this field for field");
      createCaseSample.setLatitude("37.2431");
      createCaseSample.setLongitude("115.7930");
      createCaseSample.setEstabUprn("NrpUbatse");

      // WHEN
      rabbitQueueHelper.sendMessage(inboundQueue, createCaseSample);

      // THEN
      ResponseManagementEvent responseManagementEvent =
          rhCaseQueueSpy.checkExpectedMessageReceived();
      assertEquals(EventTypeDTO.CASE_CREATED, responseManagementEvent.getEvent().getType());
      assertThat(responseManagementEvent.getPayload().getCollectionCase().getCreatedDateTime())
          .isNotNull();
      assertThat(responseManagementEvent.getPayload().getCollectionCase().getLastUpdated())
          .isNotNull();

      ResponseManagementEvent responseManagementEventToField =
          fieldOutboundQueue.checkExpectedMessageReceived();

      assertThat(responseManagementEventToField.getPayload().getMetadata()).isNotNull();
      assertEquals(
          ActionInstructionType.CREATE,
          responseManagementEventToField.getPayload().getMetadata().getFieldDecision());
      assertEquals(
          EventTypeDTO.CLERICAL_ADDRESS_RESOLUTION,
          responseManagementEventToField.getPayload().getMetadata().getCauseEventType());

      rhUacQueueSpy.checkMessageIsNotReceived(5);

      List<EventTypeDTO> eventTypesSeenDTO = new LinkedList<>();
      responseManagementEvent = actionSchedulerQueueSpy.checkExpectedMessageReceived();
      eventTypesSeenDTO.add(responseManagementEvent.getEvent().getType());

      assertThat(eventTypesSeenDTO, containsInAnyOrder(EventTypeDTO.CASE_CREATED));

      List<Case> caseList = caseRepository.findAll();
      assertEquals(1, caseList.size());
      assertEquals("ABC123", caseList.get(0).getPostcode());
      assertNull(caseList.get(0).getCeExpectedCapacity());
      assertThat(caseList.get(0).getCreatedDateTime()).isNotNull();
      assertThat(caseList.get(0).getLastUpdated()).isNotNull();

      List<Event> eventList = eventRepository.findAll();
      assertThat(eventList.size()).isEqualTo(1);
      Event actualEvent = eventList.get(0);
      assertEquals(actualEvent.getEventChannel(), BULK_PROCESSING_EVENT_CHANNEL);
      assertEquals(actualEvent.getEventDescription(), CREATE_BULK_CASE_SAMPLE_RECEIVED);

      assertEquals(uacQidLinkRepository.count(), 0);

      CreateCaseSample actualcreateCaseSample =
          objectMapper.readValue(actualEvent.getEventPayload(), CreateCaseSample.class);

      assertThat(actualcreateCaseSample).isEqualTo(createCaseSample);
    }
  }

  @Test
  public void test1000SamplesExercisingUacQidCaching() throws InterruptedException {
    // GIVEN
    int expectedSize = 1000;

    List<String> treatmentCodes =
        Arrays.asList(
            "HH_LF3R2E",
            "HH_LF3R2W",
            "HH_LF3R2N",
            "SPG_LF3R2E",
            "SPG_LF3R2W",
            "SPG_LF3R2N",
            "CE_LF3R2E",
            "CE_LF3R2W",
            "CE_LF3R2N");
    Random random = new Random();

    List<CreateCaseSample> createCaseSamples = new ArrayList<>();

    for (int i = 0; i < expectedSize; i++) {
      CreateCaseSample createCaseSample = new CreateCaseSample();
      createCaseSample.setAddressType("HH");
      createCaseSample.setPostcode("ABC123");
      createCaseSample.setRegion("E12000009");
      createCaseSample.setSecureEstablishment(0);
      String treatmentCode = treatmentCodes.get(random.nextInt(treatmentCodes.size()));
      createCaseSample.setTreatmentCode(treatmentCode);
      if (treatmentCode.startsWith("HH") || random.nextBoolean()) {
        createCaseSample.setAddressLevel("U");
      } else {
        createCaseSample.setAddressLevel("E");
      }
      createCaseSamples.add(createCaseSample);
    }

    // WHEN
    createCaseSamples.stream()
        .parallel()
        .forEach(
            c -> {
              rabbitQueueHelper.sendMessage(inboundQueue, c);
            });

    for (int i = 0; i < 20; i++) {
      Thread.sleep(2000);
      List<Case> caseList = caseRepository.findAll();

      if (caseList.size() < expectedSize) {
        continue;
      }

      break;
    }

    assertEquals(expectedSize, caseRepository.findAll().size());

    // Check all used values are unique
    List<UacQidLink> uacQids = uacQidLinkRepository.findAll();

    Set<String> uniqueQids = uacQids.stream().map(UacQidLink::getQid).collect(Collectors.toSet());
    assertThat(uniqueQids.size()).isEqualTo(uacQids.size());

    Set<String> uniqueUacs = uacQids.stream().map(UacQidLink::getUac).collect(Collectors.toSet());
    assertThat(uniqueUacs.size()).isEqualTo(uacQids.size());
  }
}

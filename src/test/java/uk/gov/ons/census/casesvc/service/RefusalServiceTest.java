package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementRefusalEvent;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@RunWith(MockitoJUnitRunner.class)
public class RefusalServiceTest {
  private static final String REFUSAL_RECEIVED = "Refusal Received";
  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock private CaseRepository caseRepository;

  @Mock private CaseService caseService;

  @Mock private EventLogger eventLogger;

  @InjectMocks RefusalService underTest;

  @Test
  public void shouldProcessARefusalReceivedMessageSuccessfully() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementRefusalEvent();
    CollectionCase collectionCase = managementEvent.getPayload().getRefusal().getCollectionCase();
    collectionCase.setId(TEST_CASE_ID.toString());
    collectionCase.setRefusalReceived(false);
    Case testCase = getRandomCase();

    when(caseService.getCaseByCaseId(TEST_CASE_ID)).thenReturn(testCase);

    // WHEN
    underTest.processRefusal(managementEvent);

    // THEN
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseRepository).saveAndFlush(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();

    assertThat(actualCase.isRefusalReceived()).isTrue();

    verify(caseService, times(1)).emitCaseUpdatedEvent(testCase);
    verify(eventLogger, times(1))
        .logCaseEvent(
            eq(testCase),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class),
            eq(REFUSAL_RECEIVED),
            eq(EventType.REFUSAL_RECEIVED),
            eq(managementEvent.getEvent()),
            anyString());
  }
}
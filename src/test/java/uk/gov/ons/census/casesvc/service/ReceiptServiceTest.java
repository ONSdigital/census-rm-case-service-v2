package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.service.ReceiptService.QID_RECEIPTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.*;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ReceiptDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class ReceiptServiceTest {

  @Mock private CaseService caseService;

  @Mock private UacQidLinkRepository uacQidLinkRepository;

  @Mock private CaseRepository caseRepository;

  @Mock private UacService uacService;

  @Mock private EventLogger eventLogger;

  @InjectMocks ReceiptService underTest;

  @Test
  public void testGoodReceipt() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    ReceiptDTO expectedReceipt = managementEvent.getPayload().getReceipt();

    // Given
    Case expectedCase = getRandomCase();
    UacQidLink expectedUacQidLink = generateRandomUacQidLink(expectedCase);

    managementEvent.getPayload().getReceipt().setResponseDateTime(OffsetDateTime.now());

    when(uacQidLinkRepository.findByQid(expectedReceipt.getQuestionnaireId()))
        .thenReturn(Optional.of(expectedUacQidLink));

    // when
    underTest.processReceipt(managementEvent);

    // then
    verify(uacQidLinkRepository, times(1)).saveAndFlush(expectedUacQidLink);
    verify(caseRepository, times(1)).saveAndFlush(expectedCase);
    verify(uacService, times(1)).emitUacUpdatedEvent(expectedUacQidLink, expectedCase, false);
    verify(caseService, times(1)).emitCaseUpdatedEvent(expectedCase);
    verify(eventLogger, times(1))
        .logUacQidEvent(
            eq(expectedUacQidLink),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class),
            eq(QID_RECEIPTED),
            eq(EventType.RESPONSE_RECEIVED),
            eq(managementEvent.getEvent()),
            anyString());
  }

  @Test(expected = RuntimeException.class)
  public void testReceiptedQidNotFound() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    String expectedQuestionnaireId = managementEvent.getPayload().getReceipt().getQuestionnaireId();
    String expectedErrorMessage =
        String.format("Questionnaire Id '%s' not found!", expectedQuestionnaireId);

    when(uacQidLinkRepository.findByQid(anyString())).thenReturn(Optional.empty());

    try {
      // WHEN
      underTest.processReceipt(managementEvent);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }
  }
}
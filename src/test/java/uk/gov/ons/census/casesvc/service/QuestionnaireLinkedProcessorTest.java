package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementQuestionnaireLinkedEvent;

import java.util.Optional;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class QuestionnaireLinkedProcessorTest {

  private final UUID TEST_CASE_ID = UUID.randomUUID();
  private final String TEST_QID = new EasyRandom().nextObject(String.class);

  @Mock UacQidLinkRepository uacQidLinkRepository;

  @Mock CaseRepository caseRepository;

  @Mock UacProcessor uacProcessor;

  @Mock CaseProcessor caseProcessor;

  @Mock EventLogger eventLogger;

  @InjectMocks QuestionnaireLinkedProcessor underTest;

  @Test
  public void testGoodQuestionnaireLinkedForUnreceiptedCase() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();

    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(TEST_QID);

    Case testCase = getRandomCase();
    testCase.setCaseId(TEST_CASE_ID);

    UacQidLink testUacQidLink = testCase.getUacQidLinks().get(0);
    testUacQidLink.setActive(true);
    testUacQidLink.setCaze(null);

    when(uacQidLinkRepository.findByQid(TEST_QID)).thenReturn(Optional.of(testUacQidLink));
    when(caseRepository.findByCaseId(TEST_CASE_ID)).thenReturn(Optional.of(testCase));

    // WHEN
    underTest.processQuestionnaireLinked(managementEvent);

    // THEN
    verify(uacQidLinkRepository).findByQid(TEST_QID);
    verify(caseRepository).findByCaseId(TEST_CASE_ID);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacQidLinkRepository).saveAndFlush(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getCaze()).isEqualTo(testCase);
    assertThat(actualUacQidLink.isActive()).isTrue();

    verify(uacProcessor).emitUacUpdatedEvent(testUacQidLink, testCase);
    verify(eventLogger)
        .logQuestionnaireLinkedEvent(
            testUacQidLink,
            "Questionnaire Linked",
            EventType.QUESTIONNAIRE_LINKED,
            uac,
            managementEvent.getEvent());

    verifyNoMoreInteractions(uacQidLinkRepository);
    verifyNoMoreInteractions(caseRepository);
  }

  @Test
  public void testGoodQuestionnaireLinkedForReceiptedCase() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();

    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(TEST_QID);

    Case testCase = getRandomCase();
    testCase.setCaseId(TEST_CASE_ID);

    UacQidLink testUacQidLink = testCase.getUacQidLinks().get(0);
    testUacQidLink.setActive(false);
    testUacQidLink.setCaze(null);

    when(uacQidLinkRepository.findByQid(TEST_QID)).thenReturn(Optional.of(testUacQidLink));
    when(caseRepository.findByCaseId(TEST_CASE_ID)).thenReturn(Optional.of(testCase));

    // WHEN
    underTest.processQuestionnaireLinked(managementEvent);

    // THEN
    verify(uacQidLinkRepository).findByQid(TEST_QID);
    verify(caseRepository).findByCaseId(TEST_CASE_ID);

    ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseRepository).saveAndFlush(caseCaptor.capture());
    Case actualCase = caseCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(actualCase.isResponseReceived()).isTrue();

    verify(caseProcessor).emitCaseUpdatedEvent(testCase);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacQidLinkRepository).saveAndFlush(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getCaze()).isEqualTo(testCase);
    assertThat(actualUacQidLink.isActive()).isFalse();

    verify(uacProcessor).emitUacUpdatedEvent(testUacQidLink, testCase);
    verify(eventLogger)
        .logQuestionnaireLinkedEvent(
            testUacQidLink,
            "Questionnaire Linked",
            EventType.QUESTIONNAIRE_LINKED,
            uac,
            managementEvent.getEvent());

    verifyNoMoreInteractions(uacQidLinkRepository);
    verifyNoMoreInteractions(caseRepository);
  }

  @Test(expected = RuntimeException.class)
  public void testQuestionnaireLinkedQidNotFound() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    String questionnaireId = managementEvent.getPayload().getUac().getQuestionnaireId();
    String expectedErrorMessage =
        String.format("Questionnaire Id '%s' not found!", questionnaireId);

    when(uacQidLinkRepository.findByQid(questionnaireId)).thenReturn(Optional.empty());

    try {
      // WHEN
      underTest.processQuestionnaireLinked(managementEvent);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }
  }

  @Test(expected = RuntimeException.class)
  public void testQuestionnaireLinkedCaseNotFound() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getPayload().getUac().setCaseId(UUID.randomUUID().toString());
    UacDTO uac = managementEvent.getPayload().getUac();
    String questionnaireId = uac.getQuestionnaireId();
    String caseId = uac.getCaseId();
    String expectedErrorMessage = String.format("Case Id '%s' not found!", caseId);

    when(uacQidLinkRepository.findByQid(questionnaireId)).thenReturn(Optional.of(new UacQidLink()));
    when(caseRepository.findByCaseId(UUID.fromString(caseId))).thenReturn(Optional.empty());

    try {
      // WHEN
      underTest.processQuestionnaireLinked(managementEvent);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }
  }
}
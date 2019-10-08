package uk.gov.ons.census.casesvc.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.ons.census.casesvc.model.dto.ExceptionReport;
import uk.gov.ons.census.casesvc.model.dto.ExceptionReportResponse;
import uk.gov.ons.census.casesvc.model.dto.Peek;
import uk.gov.ons.census.casesvc.model.dto.SkippedMessage;

@Component
public class ExceptionManagerClient {

  private String scheme = "http";

  private String host = "localhost";

  private String port = "8666";

  public ExceptionReportResponse reportError(
      String messageHash, String service, String queue, Throwable cause) {

    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setExceptionClass(cause.getClass().getName());
    exceptionReport.setExceptionMessage(cause.getMessage());
    exceptionReport.setMessageHash(messageHash);
    exceptionReport.setService(service);
    exceptionReport.setQueue(queue);

    RestTemplate restTemplate = new RestTemplate();
    UriComponents uriComponents = createUriComponents("/reportexception");

    return restTemplate.postForObject(
        uriComponents.toUri(), exceptionReport, ExceptionReportResponse.class);
  }

  public void respondToPeek(String messageHash, byte[] payload) {

    Peek peekReply = new Peek();
    peekReply.setMessageHash(messageHash);
    peekReply.setMessagePayload(payload);

    RestTemplate restTemplate = new RestTemplate();
    UriComponents uriComponents = createUriComponents("/peekreply");

    restTemplate.postForObject(uriComponents.toUri(), peekReply, Void.class);
  }

  public void storeMessageBeforeSkipping(
      String messageHash, byte[] payload, String service, String queue) {

    SkippedMessage skippedMessage = new SkippedMessage();
    skippedMessage.setMessageHash(messageHash);
    skippedMessage.setMessagePayload(payload);
    skippedMessage.setService(service);
    skippedMessage.setQueue(queue);

    RestTemplate restTemplate = new RestTemplate();
    UriComponents uriComponents = createUriComponents("/storeskippedmessage");

    restTemplate.postForObject(uriComponents.toUri(), skippedMessage, Void.class);
  }

  private UriComponents createUriComponents(String path) {
    return UriComponentsBuilder.newInstance()
        .scheme(scheme)
        .host(host)
        .port(port)
        .path(path)
        .build()
        .encode();
  }
}
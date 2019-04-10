package uk.gov.ons.census.casesvc.utility;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;

@RunWith(MockitoJUnitRunner.class)
public class QidCreatorTest {

  @Test
  public void testValidQid() {
    // Given
    QidCreator underTest = new QidCreator();

    // When
    long result = underTest.createQid(12, 2, 12345);

    // Then
    assertEquals(1220000001234599L, result);
  }

  @Test
  public void testValidCheckDigits() {
    // Given
    QidCreator underTest = new QidCreator();

    // When
    long result = underTest.createQid(12, 2, 12345);

    // Then
    String stringResult = Long.toString(result);
    assertEquals("99", stringResult.substring(stringResult.length() - 2));
  }
}

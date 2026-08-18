package pms.whq.swt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CardWindowManagerTest {

  @Test
  void baseNumberOfZeroStaysZero() {
    assertEquals(0, CardWindowManager.scaleNumAppearing(0, 4));
    assertEquals(0, CardWindowManager.scaleNumAppearing(0, 6));
  }

  @Test
  void scalesProportionallyToDefaultPartySizeOfFour() {
    assertEquals(3, CardWindowManager.scaleNumAppearing(3, 4));
    assertEquals(2, CardWindowManager.scaleNumAppearing(2, 4));
  }

  @Test
  void roundsToNearestInsteadOfTruncating() {
    // 3 monstruos base con una party de 5: 3*5/4 = 3.75 -> debe redondear a 4, no truncar a 3.
    assertEquals(4, CardWindowManager.scaleNumAppearing(3, 5));
    // 5 monstruos base con una party de 3: 5*3/4 = 3.75 -> redondea a 4.
    assertEquals(4, CardWindowManager.scaleNumAppearing(5, 3));
  }

  @Test
  void neverScalesBelowOneWhenBaseNumberIsNotZero() {
    assertEquals(1, CardWindowManager.scaleNumAppearing(1, 1));
    assertEquals(1, CardWindowManager.scaleNumAppearing(1, 2));
  }
}

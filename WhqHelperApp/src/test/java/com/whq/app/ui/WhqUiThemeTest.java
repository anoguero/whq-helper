package com.whq.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.eclipse.swt.graphics.RGB;
import org.junit.jupiter.api.Test;

import com.whq.app.model.CardType;

class WhqUiThemeTest {

  @Test
  void everyCardTypeHasItsTitleAccent() {
    for (CardType type : CardType.values()) {
      assertNotNull(WhqUiTheme.cardTypeAccent(type), type.name());
    }
    assertEquals(new RGB(30, 102, 182), WhqUiTheme.cardTypeAccent(CardType.DUNGEON_ROOM));
    assertEquals(new RGB(239, 68, 30), WhqUiTheme.cardTypeAccent(CardType.OBJECTIVE_ROOM));
    assertEquals(new RGB(71, 190, 122), WhqUiTheme.cardTypeAccent(CardType.CORRIDOR));
    assertEquals(new RGB(187, 127, 255), WhqUiTheme.cardTypeAccent(CardType.SPECIAL));
  }
}

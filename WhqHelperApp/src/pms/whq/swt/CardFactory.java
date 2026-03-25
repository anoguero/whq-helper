package pms.whq.swt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Pattern;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;

import com.whq.app.i18n.I18n;

import pms.whq.data.Event;
import pms.whq.data.Monster;
import pms.whq.data.Rule;
import pms.whq.data.SettlementEvent;
import pms.whq.data.SpecialContainer;
import pms.whq.data.TravelEvent;

public final class CardFactory {

  private static final int BORDER_WIDTH = 8;
  private static final int TITLE_HEIGHT = 40;
  private static final int TYPE_CIRCLE_SIZE = 32;
  private static final String TREASURE_TEMPLATE_PATH = "resources/treasure-card-template.png";
  private static final String TREASURE_COIN_PATH = "data/graphics/gold.png";
  private static final String EVENT_PAPER_TEXTURE_PATH = "data/graphics/paper-bg.gif";
  private static final int TREASURE_BASE_WIDTH = 847;
  private static final int TREASURE_BASE_HEIGHT = 1264;
  private static final double CARD_WINDOW_SCALE = 1.2d;

  private CardFactory() {
  }

  public static Shell createTreasureCard(Shell parent, Event event, int cardWidth, int cardHeight) {
    String title = event == null ? I18n.t("card.treasure.defaultName") : nullSafe(event.name);
    Shell shell = createBaseCardShell(parent, title, cardWidth, cardHeight);
    new TreasureCardComposite(shell, event);
    return shell;
  }

  public static Composite createTreasureCardPreview(Composite parent, Event event) {
    return new TreasureCardComposite(parent, event);
  }

  public static Shell createEventCard(Shell parent, Event event, int cardWidth, int cardHeight) {
    String title = event == null ? I18n.t("card.event.defaultName") : nullSafe(event.name);
    Shell shell = createBaseCardShell(parent, title, cardWidth, cardHeight);
    createEventCardContents(shell, event, getEventBadge(event), isTravelOrSettlementEvent(event));
    return shell;
  }

  public static Composite createEventCardPreview(Composite parent, Event event) {
    return createEventCardContents(parent, event, getEventBadge(event), isTravelOrSettlementEvent(event));
  }

  public static Composite createEventCardPreview(
      Composite parent, Event event, String badge, boolean travelOrSettlementStyle) {
    return createEventCardContents(parent, event, badge, travelOrSettlementStyle);
  }

  private static Composite createEventCardContents(
      Composite parent, Event event, String badge, boolean travelOrSettlementStyle) {
    String title = event == null ? I18n.t("card.event.defaultName") : nullSafe(event.name);
    CardComposite card = new CardComposite(parent, title, badge, CardStyle.CLASSIC_EVENT);
    Composite content = card.getContent();
    content.setLayout(new GridLayout(1, false));

    Color bg = card.getCardBackground();
    ScrolledComposite bodyScroll = new ScrolledComposite(content, SWT.V_SCROLL | SWT.BORDER);
    bodyScroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    bodyScroll.setExpandHorizontal(true);
    bodyScroll.setExpandVertical(true);
    bodyScroll.setBackground(bg);

    Composite body = new Composite(bodyScroll, SWT.NONE);
    GridLayout bodyLayout = new GridLayout(1, false);
    bodyLayout.marginWidth = 8;
    bodyLayout.marginHeight = 8;
    bodyLayout.verticalSpacing = 10;
    body.setLayout(bodyLayout);
    body.setBackground(bg);

    String metadata = buildTreasureMetadata(event);
    if (!isBlank(metadata)) {
      createWrappedLabel(body, metadata, bg);
    }

    if (!isBlank(event.flavor)) {
      createWrappedLabel(body, event.flavor, bg);
    }

    if (!isBlank(event.rules)) {
      createWrappedLabel(body, event.rules, bg);
    }

    if (!isBlank(event.special)) {
      createWrappedLabel(body, event.special, bg);
    }

    bodyScroll.setContent(body);
    bodyScroll.addControlListener(
        new ControlAdapter() {
          @Override
          public void controlResized(ControlEvent event) {
            updateScrolledContentSize(bodyScroll, body);
          }
        });
    updateScrolledContentSize(bodyScroll, body);

    if (!travelOrSettlementStyle) {
      Composite bottomBar = new Composite(content, SWT.NONE);
      bottomBar.setLayoutData(new GridData(SWT.FILL, SWT.END, true, false));
      bottomBar.setLayout(new FillLayout());
      bottomBar.setBackground(card.getFooterBackground());

      GridData bottomData = (GridData) bottomBar.getLayoutData();
      bottomData.heightHint = 30;

      if (!event.treasure) {
        Label noTreasure = new Label(bottomBar, SWT.CENTER | SWT.WRAP);
        noTreasure.setText(I18n.t("card.noTreasure"));
        noTreasure.setBackground(bottomBar.getBackground());
        noTreasure.setForeground(content.getDisplay().getSystemColor(SWT.COLOR_YELLOW));
      }
    }

    return card;
  }

  public static Shell createAdventureMissionCard(
      Shell parent,
      String title,
      String flavorText,
      String rulesText,
      int cardWidth,
      int cardHeight) {
    Shell shell = createBaseCardShell(parent, title, cardWidth, cardHeight);
    CardComposite card = new CardComposite(shell, title, "MS", CardStyle.CLASSIC_EVENT);

    Composite content = card.getContent();
    content.setLayout(new GridLayout(1, false));

    Color bg = card.getCardBackground();
    ScrolledComposite bodyScroll = new ScrolledComposite(content, SWT.V_SCROLL | SWT.BORDER);
    bodyScroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    bodyScroll.setExpandHorizontal(true);
    bodyScroll.setExpandVertical(true);
    bodyScroll.setBackground(bg);

    Composite body = new Composite(bodyScroll, SWT.NONE);
    GridLayout bodyLayout = new GridLayout(1, false);
    bodyLayout.marginWidth = 8;
    bodyLayout.marginHeight = 8;
    bodyLayout.verticalSpacing = 10;
    body.setLayout(bodyLayout);
    body.setBackground(bg);

    if (!isBlank(flavorText)) {
      createSectionLabel(body, I18n.t("dialog.mission.ambience"), bg);
      createWrappedLabel(body, flavorText, bg);
    }

    if (!isBlank(rulesText)) {
      createSectionLabel(body, I18n.t("dialog.mission.specialRules"), bg);
      createWrappedLabel(body, rulesText, bg);
    }

    bodyScroll.setContent(body);
    bodyScroll.addControlListener(
        new ControlAdapter() {
          @Override
          public void controlResized(ControlEvent event) {
            updateScrolledContentSize(bodyScroll, body);
          }
        });
    updateScrolledContentSize(bodyScroll, body);
    return shell;
  }

  public static Shell createMonsterCard(
      Shell parent,
      Monster monster,
      String titleText,
      SpecialContainer altSpecials,
      boolean appendSpecials,
      Map<String, Rule> rules,
      Image monsterImage,
      int cardWidth,
      int cardHeight) {

    Shell shell = createBaseCardShell(parent, titleText, cardWidth, cardHeight);
    createMonsterCardContents(shell, monster, titleText, altSpecials, appendSpecials, rules, monsterImage);
    return shell;
  }

  public static Composite createMonsterCardPreview(
      Composite parent,
      Monster monster,
      String titleText,
      SpecialContainer altSpecials,
      boolean appendSpecials,
      Map<String, Rule> rules,
      Image monsterImage) {
    return createMonsterCardContents(parent, monster, titleText, altSpecials, appendSpecials, rules, monsterImage);
  }

  private static Composite createMonsterCardContents(
      Composite parent,
      Monster monster,
      String titleText,
      SpecialContainer altSpecials,
      boolean appendSpecials,
      Map<String, Rule> rules,
      Image monsterImage) {
    CardComposite card = new CardComposite(parent, titleText, "M", CardStyle.CLASSIC_EVENT);
    Composite content = card.getContent();
    content.setLayout(new GridLayout(1, false));
    Color bg = card.getCardBackground();
    Font statsLabelFont = createNamedFont(content, 9, SWT.BOLD, "Newtext Bk BT", "Times New Roman");
    Font statsValueFont = createNamedFont(content, 11, SWT.BOLD, "Newtext Bk BT", "Times New Roman");
    Font sectionFont = createNamedFont(content, 10, SWT.BOLD, "Casablanca Antique", "Times New Roman");
    Font bodyFont = createNamedFont(content, 10, SWT.NORMAL, "Newtext Bk BT", "Times New Roman");
    Font bodyBoldFont = createNamedFont(content, 10, SWT.BOLD, "Newtext Bk BT", "Times New Roman");
    Image goldCoin = loadCardImage(content, TREASURE_COIN_PATH);
    parent.addDisposeListener(
        event -> {
          statsLabelFont.dispose();
          statsValueFont.dispose();
          sectionFont.dispose();
          bodyFont.dispose();
          bodyBoldFont.dispose();
          if (goldCoin != null && !goldCoin.isDisposed()) {
            goldCoin.dispose();
          }
        });

    Composite topPanel = new Composite(content, SWT.BORDER);
    topPanel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    GridLayout topLayout = new GridLayout(monsterImage == null ? 1 : 2, false);
    topLayout.marginWidth = 6;
    topLayout.marginHeight = 6;
    topLayout.horizontalSpacing = 8;
    topPanel.setLayout(topLayout);
    topPanel.setBackground(bg);

    if (monsterImage != null) {
      Canvas portrait = createMonsterPortrait(topPanel, monsterImage, bg);
      GridData portraitData = new GridData(SWT.FILL, SWT.FILL, false, true);
      portraitData.widthHint = 108;
      portraitData.heightHint = 132;
      portrait.setLayoutData(portraitData);
    }

    Composite stats = new Composite(topPanel, SWT.NONE);
    stats.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    GridLayout statsLayout = new GridLayout(4, false);
    statsLayout.marginWidth = 0;
    statsLayout.marginHeight = 0;
    statsLayout.horizontalSpacing = 6;
    statsLayout.verticalSpacing = 4;
    stats.setLayout(statsLayout);
    stats.setBackground(bg);

    addMonsterStat(stats, "M", monster.move, bg, statsLabelFont, statsValueFont);
    addMonsterStat(stats, "WS", monster.weaponskill, bg, statsLabelFont, statsValueFont);
    addMonsterStat(stats, "BS", monster.ballisticskill, bg, statsLabelFont, statsValueFont);
    addMonsterStat(stats, "S", monster.strength, bg, statsLabelFont, statsValueFont);
    addMonsterStat(stats, "T", formatToughness(monster), bg, statsLabelFont, statsValueFont);
    addMonsterStat(stats, "W", monster.wounds, bg, statsLabelFont, statsValueFont);
    addMonsterStat(stats, "I", monster.initiative, bg, statsLabelFont, statsValueFont);
    addMonsterStat(stats, "A", monster.attacks, bg, statsLabelFont, statsValueFont);
    addMonsterStat(stats, "Dmg", monster.damage, bg, statsLabelFont, statsValueFont);

    Label toHitHeader = new Label(content, SWT.CENTER | SWT.TRANSPARENT);
    toHitHeader.setText(I18n.t("card.monster.toHit"));
    toHitHeader.setForeground(content.getDisplay().getSystemColor(SWT.COLOR_BLACK));
    toHitHeader.setFont(sectionFont);
    toHitHeader.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));

    Composite toHit = new Composite(content, SWT.BORDER);
    toHit.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    GridLayout toHitLayout = new GridLayout(11, true);
    toHitLayout.marginWidth = 2;
    toHitLayout.marginHeight = 2;
    toHitLayout.horizontalSpacing = 1;
    toHitLayout.verticalSpacing = 1;
    toHit.setLayout(toHitLayout);
    toHit.setBackground(bg);

    addToHitCell(toHit, "WS", bg, true, bodyBoldFont);
    for (int ws = 1; ws <= 10; ws++) {
      addToHitCell(toHit, Integer.toString(ws), bg, false, bodyFont);
    }

    addToHitCell(toHit, "Hit", bg, true, bodyBoldFont);
    int monsterWs = parseInt(monster.weaponskill);
    for (int ws = 1; ws <= 10; ws++) {
      addToHitCell(toHit, Integer.toString(getRollNeeded(monsterWs, ws)), bg, false, bodyFont);
    }

    Composite specialSection = new Composite(content, SWT.BORDER | SWT.DOUBLE_BUFFERED);
    specialSection.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    GridLayout specialLayout = new GridLayout(1, false);
    specialLayout.marginWidth = 4;
    specialLayout.marginHeight = 4;
    specialSection.setLayout(specialLayout);
    specialSection.setBackgroundMode(SWT.INHERIT_FORCE);
    specialSection.addPaintListener(CardFactory::paintMonsterSpecialBackground);

    Label label = new Label(specialSection, SWT.NONE);
    label.setText(I18n.t("card.monster.specialRules"));
    label.setFont(sectionFont);
    label.setBackground(bg);

    ScrolledComposite specialScroll = new ScrolledComposite(specialSection, SWT.V_SCROLL | SWT.BORDER);
    specialScroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    specialScroll.setExpandHorizontal(true);
    specialScroll.setExpandVertical(true);
    specialScroll.setBackground(bg);

    Composite specialInner = new Composite(specialScroll, SWT.NONE);
    specialInner.setLayout(new GridLayout(1, false));
    specialInner.setBackground(bg);

    String specialText = buildSpecialText(monster, altSpecials, appendSpecials);
    if (!specialText.isEmpty()) {
      Label specialTextLabel = new Label(specialInner, SWT.WRAP);
      specialTextLabel.setText(specialText);
      specialTextLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
      specialTextLabel.setFont(bodyFont);
      specialTextLabel.setBackground(bg);
    }

    List<LinkEntry> links = buildLinkEntries(monster, altSpecials, appendSpecials, rules);
    for (LinkEntry entry : links) {
      Link link = new Link(specialInner, SWT.WRAP);
      link.setText("<a href=\"" + xmlEscape(entry.id) + "\">" + xmlEscape(entry.text) + "</a>");
      link.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
      link.setBackground(bg);
      link.setFont(bodyBoldFont);
      String tooltip = buildRuleTooltip(entry.id, rules);
      if (!tooltip.isEmpty()) {
        link.setToolTipText(tooltip);
      }
    }

    specialScroll.setContent(specialInner);
    specialScroll.setMinSize(specialInner.computeSize(SWT.DEFAULT, SWT.DEFAULT));

    Composite footer = new Composite(specialSection, SWT.NONE);
    footer.setLayoutData(new GridData(SWT.FILL, SWT.END, true, false));
    footer.setLayout(new GridLayout(2, false));
    footer.setBackground(bg);

    Canvas coinCanvas = new Canvas(footer, SWT.DOUBLE_BUFFERED);
    coinCanvas.setBackground(bg);
    GridData coinData = new GridData(SWT.END, SWT.CENTER, false, false);
    coinData.widthHint = 40;
    coinData.heightHint = 40;
    coinCanvas.setLayoutData(coinData);
    coinCanvas.addPaintListener(
        event -> {
          if (goldCoin == null || goldCoin.isDisposed()) {
            return;
          }
          Rectangle source = goldCoin.getBounds();
          Rectangle area = coinCanvas.getClientArea();
          event.gc.setAdvanced(true);
          event.gc.setAntialias(SWT.ON);
          event.gc.setInterpolation(SWT.HIGH);
          event.gc.drawImage(goldCoin, 0, 0, source.width, source.height, 0, 0, area.width, area.height);
        });

    Label goldValue = new Label(footer, SWT.NONE);
    goldValue.setText(nullSafe(monster.gold) + "g");
    goldValue.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));
    goldValue.setBackground(bg);
    goldValue.setFont(bodyBoldFont);

    return card;
  }

  private static Shell createBaseCardShell(Shell parent, String title, int cardWidth, int cardHeight) {
    Shell shell = new Shell(parent.getDisplay(), SWT.SHELL_TRIM | SWT.RESIZE);
    shell.setText(isBlank(title) ? "Card" : title);
    shell.setLayout(new FillLayout());

    int scaledWidth = Math.max(1, (int) Math.round(cardWidth * CARD_WINDOW_SCALE));
    int scaledHeight = Math.max(1, (int) Math.round(cardHeight * CARD_WINDOW_SCALE));
    Rectangle trim = shell.computeTrim(0, 0, scaledWidth, scaledHeight);
    shell.setSize(trim.width, trim.height);

    return shell;
  }

  private static Label createWrappedLabel(Composite parent, String text, Color background) {
    Label label = new Label(parent, SWT.WRAP);
    label.setText(nullSafe(text));
    label.setBackground(background);
    label.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    Font bodyFont = createNamedFont(parent, 10, SWT.BOLD, "Newtext Bk BT", "Times New Roman");
    label.setFont(bodyFont);
    label.addDisposeListener(event -> bodyFont.dispose());
    return label;
  }

  private static Label createSectionLabel(Composite parent, String text, Color background) {
    Label label = new Label(parent, SWT.WRAP);
    label.setText(nullSafe(text));
    label.setBackground(background);
    label.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    Font sectionFont = createNamedFont(parent, 12, SWT.BOLD, "Casablanca Antique", "Times New Roman");
    label.setFont(sectionFont);
    label.addDisposeListener(event -> sectionFont.dispose());
    return label;
  }

  private static void updateScrolledContentSize(ScrolledComposite scroll, Composite content) {
    if (scroll == null || scroll.isDisposed() || content == null || content.isDisposed()) {
      return;
    }

    Rectangle area = scroll.getClientArea();
    int widthHint = area.width <= 0 ? SWT.DEFAULT : Math.max(0, area.width - 2);
    Point preferred = content.computeSize(widthHint, SWT.DEFAULT, true);
    content.setSize(preferred);
    scroll.setMinSize(preferred);
  }

  private static Canvas createMonsterPortrait(Composite parent, Image monsterImage, Color background) {
    Canvas portrait = new Canvas(parent, SWT.DOUBLE_BUFFERED);
    portrait.setBackground(background);
    portrait.addPaintListener(
        event -> {
          Rectangle area = portrait.getClientArea();
          if (area.width <= 0 || area.height <= 0) {
            return;
          }

          GC gc = event.gc;
          gc.setAdvanced(true);
          gc.setAntialias(SWT.ON);
          gc.setBackground(background);
          gc.fillRoundRectangle(0, 0, area.width - 1, area.height - 1, 12, 12);
          gc.setForeground(portrait.getDisplay().getSystemColor(SWT.COLOR_BLACK));
          gc.drawRoundRectangle(0, 0, area.width - 1, area.height - 1, 12, 12);

          if (monsterImage == null || monsterImage.isDisposed()) {
            return;
          }

          Rectangle source = monsterImage.getBounds();
          int inset = 6;
          int targetWidth = Math.max(1, area.width - (inset * 2));
          int targetHeight = Math.max(1, area.height - (inset * 2));
          double scale = Math.min((double) targetWidth / source.width, (double) targetHeight / source.height);
          int drawWidth = Math.max(1, (int) Math.round(source.width * scale));
          int drawHeight = Math.max(1, (int) Math.round(source.height * scale));
          int drawX = inset + ((targetWidth - drawWidth) / 2);
          int drawY = inset + ((targetHeight - drawHeight) / 2);
          gc.setInterpolation(SWT.HIGH);
          gc.drawImage(monsterImage, 0, 0, source.width, source.height, drawX, drawY, drawWidth, drawHeight);
        });
    return portrait;
  }

  private static void addMonsterStat(
      Composite parent,
      String name,
      String value,
      Color background,
      Font labelFont,
      Font valueFont) {
    Label nameLabel = new Label(parent, SWT.RIGHT);
    nameLabel.setText(name + ":");
    nameLabel.setBackground(background);
    nameLabel.setFont(labelFont);
    nameLabel.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));

    Label valueLabel = new Label(parent, SWT.LEFT);
    valueLabel.setText(nullSafe(value));
    valueLabel.setBackground(background);
    valueLabel.setFont(valueFont);
    valueLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));
  }

  private static void addToHitCell(Composite parent, String value, Color background, boolean header, Font font) {
    Label cell = new Label(parent, SWT.CENTER | SWT.BORDER);
    cell.setText(value);
    cell.setBackground(background);
    if (font != null) {
      cell.setFont(font);
    }
    cell.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    cell.setForeground(parent.getDisplay().getSystemColor(header ? SWT.COLOR_BLACK : SWT.COLOR_DARK_GRAY));
  }

  private static void paintMonsterSpecialBackground(PaintEvent event) {
    Rectangle bounds = ((Composite) event.widget).getClientArea();
    if (bounds.width <= 0 || bounds.height <= 0) {
      return;
    }

    Color start = new Color(event.display, 244, 238, 220);
    Color end = new Color(event.display, 225, 212, 184);
    try {
      Pattern pattern = new Pattern(event.display, bounds.width / 2f, 0, bounds.width / 2f, bounds.height, start, end);
      event.gc.setBackgroundPattern(pattern);
      event.gc.fillRectangle(bounds);
      event.gc.setBackgroundPattern(null);
      pattern.dispose();
    } finally {
      start.dispose();
      end.dispose();
    }
  }

  private static int getRollNeeded(int weaponSkill, int targetWs) {
    if ((targetWs < (weaponSkill - 5)) || ((weaponSkill > 2) && (targetWs == 1))) {
      return 2;
    }
    if (targetWs < weaponSkill) {
      return 3;
    }
    if (targetWs <= (weaponSkill * 2)) {
      return 4;
    }
    if (targetWs <= (weaponSkill * 3)) {
      return 5;
    }
    return 6;
  }

  private static String formatToughness(Monster monster) {
	if (monster.toughness.isEmpty()) {
		return "";
	}
    if (monster.armor != null && !monster.armor.isEmpty() && !"-".equals(monster.armor) && !"S".equals(monster.toughness) && !"S".equals(monster.armor)) {
    	int totalToughness = Integer.parseInt(monster.toughness) + Integer.parseInt(monster.armor);
      return nullSafe(monster.toughness) + " (" + totalToughness + ")";
    }
    return nullSafe(monster.toughness);
  }

  private static String buildSpecialText(Monster monster, SpecialContainer altSpecials, boolean appendSpecials) {
    boolean includeAlt = altSpecials != null && altSpecials.hasAnySpecial();
    boolean includeMonster = monster.hasAnySpecial() && (appendSpecials || !includeAlt);

    StringBuilder builder = new StringBuilder();

    if (includeMonster && monster.hasSpecialText()) {
      builder.append(monster.special);
      if (includeAlt && altSpecials.hasSpecialText()) {
        builder.append('\n');
      }
    }

    if (includeAlt && altSpecials.hasSpecialText()) {
      builder.append(altSpecials.special);
    }

    return builder.toString().trim();
  }

  private static List<LinkEntry> buildLinkEntries(
      Monster monster,
      SpecialContainer altSpecials,
      boolean appendSpecials,
      Map<String, Rule> rules) {

    List<LinkEntry> links = new ArrayList<>();

    boolean includeAlt = altSpecials != null && altSpecials.hasAnySpecial();
    boolean includeMonster = monster.hasAnySpecial() && (appendSpecials || !includeAlt);

    boolean anyLinks =
        monster.hasSpecialLinks() || (includeAlt && altSpecials != null && altSpecials.hasSpecialLinks());

    if (includeMonster) {
      if (monster.hasMagic()) {
        boolean moreSpecials = anyLinks || (includeAlt && altSpecials != null && altSpecials.hasMagic());
        addMagic(links, monster.magicType, monster.magicLevel, rules, moreSpecials);
      }
      addSpecials(links, monster.specialLinks);
    }

    if (includeAlt && altSpecials != null) {
      if (altSpecials.hasMagic()) {
        addMagic(links, altSpecials.magicType, altSpecials.magicLevel, rules, anyLinks);
      }
      addSpecials(links, altSpecials.specialLinks);
    }

    return links;
  }

  private static void addMagic(
      List<LinkEntry> links,
      String magicType,
      int magicLevel,
      Map<String, Rule> rules,
      boolean moreSpecials) {
    Rule rule = rules.get(magicType);
    String ruleName = rule != null ? rule.name : magicType;
    links.add(new LinkEntry(magicType, ruleName + " "));

    String text = "Magic " + magicLevel + (moreSpecials ? "; " : ".");
    links.add(new LinkEntry("rpb-magic", text));
  }

  private static void addSpecials(List<LinkEntry> links, Map<String, String> specials) {
    if (specials == null || specials.isEmpty()) {
      return;
    }

    int index = 0;
    int size = specials.size();
    for (Entry<String, String> entry : specials.entrySet()) {
      String punctuation = index < (size - 1) ? "; " : ".";
      links.add(new LinkEntry(entry.getKey(), nullSafe(entry.getValue()) + punctuation));
      index++;
    }
  }

  private static int parseInt(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException nfe) {
      return 0;
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static String buildTreasureMetadata(Event event) {
    if (event == null) {
      return "";
    }

    StringBuilder metadata = new StringBuilder();
    if (!isBlank(event.goldValue)) {
      metadata.append(I18n.t("card.treasure.goldValue")).append(": ").append(event.goldValue.trim());
    }
    if (!isBlank(event.users)) {
      if (!metadata.isEmpty()) {
        metadata.append('\n');
      }
      metadata.append(I18n.t("card.treasure.users")).append(": ").append(formatTreasureUsers(event.users));
    }
    return metadata.toString();
  }

  private static String formatTreasureUsers(String users) {
    if (isBlank(users)) {
      return "";
    }

    StringBuilder formatted = new StringBuilder();
    for (char code : users.trim().toUpperCase().toCharArray()) {
      String label = switch (code) {
        case 'B' -> I18n.t("card.treasure.user.barbarian");
        case 'D' -> I18n.t("card.treasure.user.dwarf");
        case 'E' -> I18n.t("card.treasure.user.elf");
        case 'W' -> I18n.t("card.treasure.user.wizard");
        default -> Character.toString(code);
      };
      if (!formatted.isEmpty()) {
        formatted.append(", ");
      }
      formatted.append(label);
    }
    return formatted.toString();
  }

  private static String nullSafe(String value) {
    return value == null ? "" : value;
  }

  private static Font createRelativeFont(Composite base, int delta) {
    FontData[] source = base.getFont().getFontData();
    FontData[] resized = new FontData[source.length];
    for (int i = 0; i < source.length; i++) {
      FontData data = source[i];
      resized[i] = new FontData(data.getName(), Math.max(8, data.getHeight() + delta), data.getStyle());
    }
    return new Font(base.getDisplay(), resized);
  }

  private static String buildRuleTooltip(String id, Map<String, Rule> rules) {
    if (isBlank(id) || rules == null) {
      return "";
    }
    Rule rule = rules.get(id);
    if (rule == null || isBlank(rule.text)) {
      return "";
    }
    String title = nullSafe(rule.name);
    if ("magic".equals(rule.type) && !title.isEmpty()) {
      title += " Magic";
    }
    if (title.isEmpty()) {
      return rule.text;
    }
    return title + "\n\n" + rule.text;
  }

  private static String xmlEscape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  private static Font createNamedFont(Composite base, int height, int style, String... names) {
    String fallback = base.getFont().getFontData()[0].getName();
    String selected = fallback;
    if (names != null) {
      for (String name : names) {
        if (!isBlank(name)) {
          selected = name;
          break;
        }
      }
    }
    return new Font(base.getDisplay(), selected, Math.max(8, height), style);
  }

  private static Image loadCardImage(Composite base, String relativePath) {
    if (isBlank(relativePath)) {
      return null;
    }
    try {
      return new Image(base.getDisplay(), relativePath);
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private static final class TreasureCardComposite extends Composite {

    private final Event event;
    private final boolean objectiveTreasure;
    private final Image templateImage;
    private final Image coinImage;
    private final Color accentColor;
    private final Color bodyTextColor;
    private final Color usersBoxColor;
    private final Canvas bodyCanvas;
    private final Label usersLabel;
    private final Font bodyFont;
    private final Font bodyBoldFont;
    private Font usersLabelFont;
    private String flavorText;
    private String rulesText;
    private int bodyScrollOffset;
    private int bodyContentHeight;

    private TreasureCardComposite(Composite parent, Event event) {
      super(parent, SWT.DOUBLE_BUFFERED);
      this.event = event;
      this.objectiveTreasure = isObjectiveTreasure(event);
      this.templateImage = loadCardImage(this, TREASURE_TEMPLATE_PATH);
      this.coinImage = loadCardImage(this, TREASURE_COIN_PATH);
      this.accentColor =
          objectiveTreasure
              ? new Color(getDisplay(), 162, 58, 46)
              : new Color(getDisplay(), 116, 151, 105);
      this.bodyTextColor = new Color(getDisplay(), 24, 18, 14);
      this.usersBoxColor = new Color(getDisplay(), 241, 236, 225);
      this.bodyFont = createNamedFont(this, 10, SWT.NORMAL, "Newtext Bk BT", "Times New Roman");
      this.bodyBoldFont = createNamedFont(this, 10, SWT.BOLD, "Newtext Bk BT", "Times New Roman");

      setLayout(null);
      setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));

      bodyCanvas = new Canvas(this, SWT.V_SCROLL | SWT.DOUBLE_BUFFERED);
      bodyCanvas.setForeground(bodyTextColor);
      bodyCanvas.addPaintListener(this::paintBodyArea);
      bodyCanvas.addControlListener(
          new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent resizeEvent) {
              updateBodyScrollState();
            }
          });
      ScrollBar verticalBar = bodyCanvas.getVerticalBar();
      if (verticalBar != null) {
        verticalBar.setIncrement(18);
        verticalBar.addListener(
            SWT.Selection,
            selectionEvent -> {
              bodyScrollOffset = verticalBar.getSelection();
              bodyCanvas.redraw();
            });
      }

      usersLabel = new Label(this, SWT.WRAP | SWT.CENTER);
      usersLabel.setBackground(usersBoxColor);
      usersLabel.setForeground(bodyTextColor);
      usersLabelFont = bodyBoldFont;
      usersLabel.setFont(usersLabelFont);
      usersLabel.setVisible(false);

      configureBodyContent();

      addControlListener(
          new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent resizeEvent) {
              layoutTreasureChildren();
            }
          });

      addPaintListener(this::paintCard);
      addDisposeListener(
          disposeEvent -> {
            accentColor.dispose();
            bodyTextColor.dispose();
            usersBoxColor.dispose();
            bodyFont.dispose();
            bodyBoldFont.dispose();
            if (usersLabelFont != null && usersLabelFont != bodyBoldFont && !usersLabelFont.isDisposed()) {
              usersLabelFont.dispose();
            }
            if (templateImage != null && !templateImage.isDisposed()) {
              templateImage.dispose();
            }
            if (coinImage != null && !coinImage.isDisposed()) {
              coinImage.dispose();
            }
          });

      layoutTreasureChildren();
    }

    private void paintCard(PaintEvent paintEvent) {
      Rectangle area = getClientArea();
      if (area.width <= 0 || area.height <= 0) {
        return;
      }

      GC gc = paintEvent.gc;
      gc.setAdvanced(true);
      gc.setAntialias(SWT.ON);
      gc.setTextAntialias(SWT.ON);

      drawTemplate(gc, area);

      float sx = area.width / (float) TREASURE_BASE_WIDTH;
      float sy = area.height / (float) TREASURE_BASE_HEIGHT;

      drawCenteredText(
          gc,
          I18n.t("card.treasure"),
          120,
          62,
          607,
          34,
          accentColor,
          SWT.BOLD,
          23,
          15,
          "Casablanca Antique",
          "Times New Roman");

      drawCenteredText(
          gc,
          nullSafe(event == null ? I18n.t("card.treasure.defaultName") : event.name).toUpperCase(),
          75,
          94,
          695,
          64,
          accentColor,
          SWT.BOLD,
          42,
          18,
          "Casablanca Antique",
          "Times New Roman");

      String footerText = buildTreasureFooterText(event, objectiveTreasure);
      drawCenteredText(
          gc,
          footerText,
          90,
          1190,
          660,
          32,
          accentColor,
          SWT.BOLD,
          24,
          12,
          "Casablanca Antique",
          "Times New Roman");
      drawGoldCoin(gc, sx, sy);
    }

    private void drawTemplate(GC gc, Rectangle area) {
      if (templateImage != null && !templateImage.isDisposed()) {
        Rectangle source = templateImage.getBounds();
        gc.drawImage(
            templateImage,
            0,
            0,
            source.width,
            source.height,
            area.x,
            area.y,
            area.width,
            area.height);
        return;
      }

      gc.setBackground(getDisplay().getSystemColor(SWT.COLOR_BLACK));
      gc.fillRoundRectangle(area.x, area.y, area.width - 1, area.height - 1, 28, 28);

      gc.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));
      int insetX = Math.round(area.width * 0.06f);
      int insetY = Math.round(area.height * 0.14f);
      int insetW = Math.round(area.width * 0.88f);
      int insetH = Math.round(area.height * 0.78f);
      gc.fillRectangle(insetX, insetY, insetW, insetH);
    }

    private void drawGoldCoin(GC gc, float sx, float sy) {
      Rectangle coinRect = scaledRect(sx, sy, 35, 1050, 110, 110);
      if (coinImage != null && !coinImage.isDisposed()) {
        Rectangle source = coinImage.getBounds();
        gc.drawImage(
            coinImage,
            0,
            0,
            source.width,
            source.height,
            coinRect.x,
            coinRect.y,
            coinRect.width,
            coinRect.height);
      } else {
        Color old = gc.getBackground();
        gc.setBackground(getDisplay().getSystemColor(SWT.COLOR_YELLOW));
        gc.fillOval(coinRect.x, coinRect.y, coinRect.width, coinRect.height);
        gc.setBackground(old);
      }

      String goldValue = nullSafe(event == null ? "" : event.goldValue).trim();
      String mainValue = goldValue;
      String footer = "";
      if (goldValue.toUpperCase().endsWith("G")) {
        mainValue = goldValue.substring(0, goldValue.length() - 1).trim();
        footer = "GOLD";
      }
      if (isBlank(mainValue)) {
        mainValue = "-";
      }

      drawCenteredText(
          gc,
          "VALUE",
          35,
          1056,
          110,
          18,
          bodyTextColor,
          SWT.BOLD,
          16,
          10,
          "Newtext Bk BT",
          "Times New Roman");
      drawCenteredText(
          gc,
          mainValue,
          35,
          1080,
          110,
          28,
          bodyTextColor,
          SWT.BOLD,
          22,
          12,
          "Newtext Bk BT",
          "Times New Roman");
      if (!isBlank(footer)) {
        drawCenteredText(
            gc,
            footer,
            35,
            1111,
            110,
            18,
            bodyTextColor,
            SWT.BOLD,
            17,
            10,
            "Newtext Bk BT",
            "Times New Roman");
      }
    }

    private void paintBodyArea(PaintEvent paintEvent) {
      Rectangle area = bodyCanvas.getClientArea();
      if (area.width <= 0 || area.height <= 0) {
        return;
      }

      GC gc = paintEvent.gc;
      gc.setAdvanced(true);
      gc.setAntialias(SWT.ON);
      gc.setTextAntialias(SWT.ON);
      paintBodyBackground(gc);

      int marginX = Math.max(8, Math.round(area.width * 0.03f));
      int marginY = Math.max(8, Math.round(area.height * 0.03f));
      int availableWidth = Math.max(50, area.width - (marginX * 2));
      int y = marginY - bodyScrollOffset;

      if (!isBlank(flavorText)) {
        TextLayout flavorLayout = createBodyLayout(flavorText, bodyBoldFont, availableWidth);
        try {
          gc.setForeground(bodyTextColor);
          flavorLayout.draw(gc, marginX, y);
          y += flavorLayout.getBounds().height + Math.max(8, Math.round(area.height * 0.025f));
        } finally {
          flavorLayout.dispose();
        }
      }

      if (!isBlank(rulesText)) {
        TextLayout rulesLayout = createBodyLayout(rulesText, bodyFont, availableWidth);
        try {
          gc.setForeground(bodyTextColor);
          rulesLayout.draw(gc, marginX, y);
        } finally {
          rulesLayout.dispose();
        }
      }
    }

    private void paintBodyBackground(GC gc) {
      Rectangle canvasBounds = bodyCanvas.getBounds();
      Rectangle cardArea = getClientArea();
      if (templateImage != null && !templateImage.isDisposed()) {
        Rectangle source = templateImage.getBounds();
        gc.drawImage(
            templateImage,
            0,
            0,
            source.width,
            source.height,
            -canvasBounds.x,
            -canvasBounds.y,
            cardArea.width,
            cardArea.height);
        return;
      }

      gc.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));
      gc.fillRectangle(0, 0, canvasBounds.width, canvasBounds.height);
    }

    private void drawCenteredText(
        GC gc,
        String text,
        int baseX,
        int baseY,
        int baseWidth,
        int baseHeight,
        Color color,
        int style,
        int maxFontSize,
        int minFontSize,
        String... fontNames) {

      if (isBlank(text)) {
        return;
      }

      Rectangle area = getClientArea();
      float sx = area.width / (float) TREASURE_BASE_WIDTH;
      float sy = area.height / (float) TREASURE_BASE_HEIGHT;
      Rectangle target = scaledRect(sx, sy, baseX, baseY, baseWidth, baseHeight);

      Font font = null;
      try {
        for (int size = maxFontSize; size >= minFontSize; size--) {
          if (font != null) {
            font.dispose();
          }
          font = createNamedFont(this, Math.round(size * sy), style, fontNames);
          gc.setFont(font);
          Point extent = gc.textExtent(text, SWT.DRAW_TRANSPARENT);
          if (extent.x <= target.width && extent.y <= (target.height + Math.round(4 * sy))) {
            break;
          }
        }

        gc.setForeground(color);
        Point extent = gc.textExtent(text, SWT.DRAW_TRANSPARENT);
        int x = target.x + ((target.width - extent.x) / 2);
        int y = target.y + ((target.height - extent.y) / 2);
        gc.drawText(text, x, y, true);
      } finally {
        if (font != null) {
          font.dispose();
        }
      }
    }

    private Rectangle scaledRect(float sx, float sy, int x, int y, int width, int height) {
      return new Rectangle(
          Math.round(x * sx),
          Math.round(y * sy),
          Math.max(1, Math.round(width * sx)),
          Math.max(1, Math.round(height * sy)));
    }

    private void configureBodyContent() {
      flavorText = buildTreasureFlavorText(event);
      rulesText = buildTreasureRulesText(event);
      if (isBlank(flavorText) && isBlank(rulesText)) {
        flavorText = buildTreasureCombinedText("", "", objectiveTreasure);
        rulesText = "";
      }

      String usersOnly = buildTreasureUsersOnlyText(event == null ? "" : event.users);
      boolean showUsers = !isBlank(usersOnly);
      usersLabel.setText(showUsers ? usersOnly : "");
    }

    private void layoutTreasureChildren() {
      Rectangle area = getClientArea();
      if (area.width <= 0 || area.height <= 0) {
        return;
      }

      float sx = area.width / (float) TREASURE_BASE_WIDTH;
      float sy = area.height / (float) TREASURE_BASE_HEIGHT;
      Rectangle scrollRect = scaledRect(sx, sy, 62, 198, 705, buildBodyHeight(event));
      bodyCanvas.setBounds(scrollRect);

      String usersOnly = buildTreasureUsersOnlyText(event == null ? "" : event.users);
      boolean showUsers = !isBlank(usersOnly);
      if (showUsers) {
        Rectangle usersRect = scaledRect(sx, sy, 168, 1061, 550, 76);
        usersLabel.setBounds(usersRect);
        usersLabel.setText(usersOnly);
        updateUsersLabelFont(usersOnly, usersRect);
        usersLabel.setVisible(true);
      } else {
        usersLabel.setVisible(false);
      }

      updateBodyScrollState();
      bodyCanvas.redraw();
    }

    private void updateBodyScrollState() {
      if (bodyCanvas == null || bodyCanvas.isDisposed()) {
        return;
      }

      Rectangle area = bodyCanvas.getClientArea();
      if (area.width <= 0 || area.height <= 0) {
        return;
      }

      int marginX = Math.max(8, Math.round(area.width * 0.03f));
      int marginY = Math.max(8, Math.round(area.height * 0.03f));
      int availableWidth = Math.max(50, area.width - (marginX * 2));
      int spacing = Math.max(8, Math.round(area.height * 0.025f));
      int contentHeight = marginY;

      if (!isBlank(flavorText)) {
        TextLayout flavorLayout = createBodyLayout(flavorText, bodyBoldFont, availableWidth);
        try {
          contentHeight += flavorLayout.getBounds().height;
        } finally {
          flavorLayout.dispose();
        }
      }

      if (!isBlank(rulesText)) {
        if (!isBlank(flavorText)) {
          contentHeight += spacing;
        }
        TextLayout rulesLayout = createBodyLayout(rulesText, bodyFont, availableWidth);
        try {
          contentHeight += rulesLayout.getBounds().height;
        } finally {
          rulesLayout.dispose();
        }
      }

      bodyContentHeight = contentHeight + marginY;
      ScrollBar verticalBar = bodyCanvas.getVerticalBar();
      if (verticalBar == null) {
        return;
      }

      int thumb = Math.max(1, area.height);
      int maxSelection = Math.max(0, bodyContentHeight - area.height);
      verticalBar.setMaximum(Math.max(bodyContentHeight, area.height));
      verticalBar.setThumb(Math.min(thumb, verticalBar.getMaximum()));
      verticalBar.setPageIncrement(thumb);
      verticalBar.setVisible(bodyContentHeight > area.height);

      if (bodyScrollOffset > maxSelection) {
        bodyScrollOffset = maxSelection;
      }
      verticalBar.setSelection(bodyScrollOffset);
    }

    private TextLayout createBodyLayout(String text, Font font, int width) {
      TextLayout layout = new TextLayout(getDisplay());
      layout.setText(nullSafe(text));
      layout.setFont(font);
      layout.setWidth(width);
      layout.setSpacing(2);
      return layout;
    }

    private void updateUsersLabelFont(String text, Rectangle bounds) {
      if (isBlank(text) || bounds == null || bounds.width <= 0 || bounds.height <= 0) {
        usersLabel.setFont(bodyBoldFont);
        usersLabelFont = bodyBoldFont;
        return;
      }

      if (usersLabelFont != null && usersLabelFont != bodyBoldFont && !usersLabelFont.isDisposed()) {
        usersLabelFont.dispose();
      }
      usersLabelFont = bodyBoldFont;

      int maxWidth = Math.max(24, bounds.width - 14);
      int maxHeight = Math.max(10, bounds.height - 8);
      int baseSize = Math.max(6, bodyBoldFont.getFontData()[0].getHeight());

      GC gc = new GC(usersLabel);
      try {
        for (int size = baseSize; size >= 6; size--) {
          Font candidate =
              size == baseSize
                  ? bodyBoldFont
                  : createNamedFont(this, size, SWT.BOLD, "Newtext Bk BT", "Times New Roman");
          gc.setFont(candidate);
          Point extent = gc.textExtent(text, SWT.DRAW_TRANSPARENT);
          if (extent.x <= maxWidth && extent.y <= maxHeight) {
            usersLabelFont = candidate;
            usersLabel.setFont(candidate);
            return;
          }
          if (candidate != bodyBoldFont) {
            candidate.dispose();
          }
        }
      } finally {
        gc.dispose();
      }

      usersLabel.setFont(bodyBoldFont);
      usersLabelFont = bodyBoldFont;
    }
  }

  private static boolean isTravelOrSettlementEvent(Event event) {
    return event instanceof TravelEvent || event instanceof SettlementEvent;
  }

  private static String getEventBadge(Event event) {
    if (event instanceof SettlementEvent) {
      return "SE";
    }
    if (event instanceof TravelEvent) {
      return "TR";
    }
    return "EV";
  }

  private static boolean isObjectiveTreasure(Event event) {
    String id = nullSafe(event == null ? "" : event.id).trim().toLowerCase();
    return event != null && event.treasure && id.contains("-objective-");
  }

  private static int buildBodyHeight(Event event) {
    return 805;
  }

  private static String buildTreasureFlavorText(Event event) {
    if (event == null) {
      return "";
    }
    return nullSafe(event.flavor).trim();
  }

  private static String buildTreasureRulesText(Event event) {
    if (event == null) {
      return "";
    }

    List<String> sections = new ArrayList<>();
    if (!isBlank(event.rules)) {
      sections.add(event.rules.trim());
    }
    if (!isBlank(event.special) && !shouldMoveSpecialToFooter(event.special)) {
      sections.add(event.special.trim());
    }
    return String.join("\n\n", sections);
  }

  private static String buildTreasureCombinedText(String flavorText, String rulesText, boolean objectiveTreasure) {
    List<String> sections = new ArrayList<>();
    if (!isBlank(flavorText)) {
      sections.add(flavorText);
    }
    if (!isBlank(rulesText)) {
      sections.add(rulesText);
    }
    if (sections.isEmpty()) {
      sections.add(objectiveTreasure ? I18n.t("card.treasure.footerObjective") : I18n.t("card.treasure.footerDungeon"));
    }
    return String.join("\n\n", sections);
  }

  private static boolean shouldMoveSpecialToFooter(String special) {
    String trimmed = nullSafe(special).trim();
    return !trimmed.isEmpty() && trimmed.length() <= 38 && !trimmed.contains("\n");
  }

  private static boolean shouldRenderTreasureUsers(String users) {
    String normalized = nullSafe(users).replaceAll("\\s+", "").toUpperCase();
    return !normalized.isEmpty() && !"BDEW".equals(normalized);
  }

  private static String buildTreasureUsersOnlyText(String users) {
    if (!shouldRenderTreasureUsers(users)) {
      return "";
    }

    List<String> labels = new ArrayList<>();
    for (char code : nullSafe(users).replaceAll("\\s+", "").toUpperCase().toCharArray()) {
      switch (code) {
        case 'B' -> labels.add(I18n.t("card.treasure.user.barbarian"));
        case 'D' -> labels.add(I18n.t("card.treasure.user.dwarf"));
        case 'E' -> labels.add(I18n.t("card.treasure.user.elf"));
        case 'W' -> labels.add(I18n.t("card.treasure.user.wizard"));
        default -> {
        }
      }
    }

    if (labels.isEmpty()) {
      return "";
    }
    if (labels.size() == 1) {
      return labels.get(0) + " " + I18n.t("card.treasure.only");
    }
    if (labels.size() == 2) {
      return labels.get(0) + " & " + labels.get(1) + " " + I18n.t("card.treasure.only");
    }

    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < labels.size(); i++) {
      if (i > 0) {
        builder.append(i == labels.size() - 1 ? " & " : ", ");
      }
      builder.append(labels.get(i));
    }
    builder.append(" ").append(I18n.t("card.treasure.only"));
    return builder.toString();
  }

  private static String buildTreasureFooterText(Event event, boolean objectiveTreasure) {
    if (event != null && shouldMoveSpecialToFooter(event.special)) {
      return event.special.trim().toUpperCase();
    }
    return objectiveTreasure ? I18n.t("card.treasure.footerObjective") : I18n.t("card.treasure.footerDungeon");
  }

  private enum CardStyle {
    DEFAULT,
    CLASSIC_EVENT
  }

  private static final class CardComposite extends Composite {

    private final String title;
    private final String typeText;
    private final CardStyle style;

    private final Composite content;

    private final Font titleFont;
    private final Font circleFont;
    private final Color parchmentTop;
    private final Color parchmentBottom;
    private final Color frameColor;
    private final Color titleBarStart;
    private final Color titleBarEnd;
    private final Color titleTextColor;
    private final Color sealColor;
    private final Color sealTextColor;
    private final Image textureImage;
    private final Image coinImage;
    private Image cachedSurface;
    private int cachedSurfaceWidth = -1;
    private int cachedSurfaceHeight = -1;

    private CardComposite(Composite parent, String title, String typeText, CardStyle style) {
      super(parent, SWT.DOUBLE_BUFFERED);
      this.title = isBlank(title) ? "" : title.toUpperCase();
      this.typeText = isBlank(typeText) ? "" : typeText.toUpperCase();
      this.style = style == null ? CardStyle.DEFAULT : style;

      setLayout(null);
      setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));

      content = new Composite(this, SWT.NONE);
      content.setBackground(getCardBackground());

      FontData baseData = getFont().getFontData()[0];
      if (this.style == CardStyle.CLASSIC_EVENT) {
        titleFont = new Font(getDisplay(), "Casablanca Antique", Math.max(13, baseData.getHeight() + 5), SWT.BOLD);
        circleFont = new Font(getDisplay(), baseData.getName(), Math.max(11, baseData.getHeight() + 1), SWT.BOLD);
        parchmentTop = new Color(getDisplay(), 242, 235, 210);
        parchmentBottom = new Color(getDisplay(), 216, 203, 170);
        frameColor = new Color(getDisplay(), 19, 16, 12);
        titleBarStart = new Color(getDisplay(), 10, 10, 8);
        titleBarEnd = new Color(getDisplay(), 10, 10, 8);
        titleTextColor = new Color(getDisplay(), 225, 192, 42);
        sealColor = new Color(getDisplay(), 233, 223, 191);
        sealTextColor = new Color(getDisplay(), 34, 27, 16);
        textureImage = loadCardImage(this, EVENT_PAPER_TEXTURE_PATH);
        coinImage = "M".equals(this.typeText) ? loadCardImage(this, TREASURE_COIN_PATH) : null;
      } else {
        titleFont = new Font(getDisplay(), baseData.getName(), Math.max(10, baseData.getHeight() + 1), SWT.BOLD);
        circleFont = new Font(getDisplay(), baseData.getName(), Math.max(16, baseData.getHeight() + 7), SWT.BOLD);
        parchmentTop = new Color(getDisplay(), 235, 226, 203);
        parchmentBottom = new Color(getDisplay(), 207, 185, 145);
        frameColor = new Color(getDisplay(), 73, 50, 31);
        titleBarStart = new Color(getDisplay(), 57, 33, 22);
        titleBarEnd = new Color(getDisplay(), 100, 63, 36);
        titleTextColor = new Color(getDisplay(), 226, 196, 120);
        sealColor = new Color(getDisplay(), 138, 76, 40);
        sealTextColor = titleTextColor;
        textureImage = null;
        coinImage = null;
      }

      addControlListener(
          new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent event) {
              disposeCachedSurface();
              layoutContent();
            }
          });

      addPaintListener(this::paintCard);
      addDisposeListener(
          event -> {
            titleFont.dispose();
            circleFont.dispose();
            parchmentTop.dispose();
            parchmentBottom.dispose();
            frameColor.dispose();
            titleBarStart.dispose();
            titleBarEnd.dispose();
            titleTextColor.dispose();
            sealColor.dispose();
            sealTextColor.dispose();
            if (textureImage != null && !textureImage.isDisposed()) {
              textureImage.dispose();
            }
            if (coinImage != null && !coinImage.isDisposed()) {
              coinImage.dispose();
            }
            disposeCachedSurface();
          });

      layoutContent();
    }

    private Composite getContent() {
      return content;
    }

    private Color getCardBackground() {
      return parchmentTop;
    }

    private Color getFooterBackground() {
      return frameColor;
    }

    private void layoutContent() {
      Rectangle area = getClientArea();
      int x = BORDER_WIDTH + 2;
      int y = BORDER_WIDTH + TITLE_HEIGHT + 3;
      int w = Math.max(12, area.width - (BORDER_WIDTH * 2) - 6);
      int h = Math.max(12, area.height - BORDER_WIDTH - y - 2);
      content.setBounds(x, y, w, h);
    }

    private void paintCard(PaintEvent event) {
      Rectangle area = getClientArea();
      if (area.width <= 0 || area.height <= 0) {
        return;
      }

      ensureCachedSurface(area.width, area.height);
      if (cachedSurface != null && !cachedSurface.isDisposed()) {
        event.gc.drawImage(cachedSurface, 0, 0);
      }
    }

    private void ensureCachedSurface(int width, int height) {
      if (width <= 0 || height <= 0) {
        return;
      }
      if (cachedSurface != null
          && !cachedSurface.isDisposed()
          && cachedSurfaceWidth == width
          && cachedSurfaceHeight == height) {
        return;
      }

      disposeCachedSurface();
      cachedSurface = new Image(getDisplay(), width, height);
      cachedSurfaceWidth = width;
      cachedSurfaceHeight = height;

      GC gc = new GC(cachedSurface);
      try {
        gc.setAntialias(SWT.ON);
        gc.setTextAntialias(SWT.ON);
        gc.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));
        gc.fillRectangle(0, 0, width, height);
        renderCard(gc, width, height);
      } finally {
        gc.dispose();
      }
    }

    private void renderCard(GC gc, int width, int height) {
      Rectangle area = new Rectangle(0, 0, width, height);
      int innerX = BORDER_WIDTH;
      int innerY = BORDER_WIDTH;
      int innerW = area.width - (BORDER_WIDTH * 2) - 2;
      int innerH = area.height - (BORDER_WIDTH * 2) - 2;
      if (innerW <= 0 || innerH <= 0) {
        return;
      }

      Color bg = getCardBackground();
      if (style == CardStyle.CLASSIC_EVENT && textureImage != null && !textureImage.isDisposed()) {
        gc.setBackground(bg);
        gc.fillRoundRectangle(innerX, innerY, innerW, innerH, 24, 24);
        Pattern texture =
            new Pattern(getDisplay(), textureImage);
        gc.setBackgroundPattern(texture);
        gc.fillRoundRectangle(innerX, innerY, innerW, innerH, 24, 24);
        gc.setBackgroundPattern(null);
        texture.dispose();
      } else {
        Pattern parchment =
            new Pattern(
                getDisplay(),
                innerX,
                innerY,
                innerX,
                innerY + innerH,
                parchmentTop,
                parchmentBottom);
        gc.setBackgroundPattern(parchment);
        gc.fillRoundRectangle(innerX, innerY, innerW, innerH, 24, 24);
        gc.setBackgroundPattern(null);
        parchment.dispose();
      }

      gc.setForeground(frameColor);
      gc.setLineWidth(3);
      gc.drawRoundRectangle(innerX, innerY, innerW, innerH, 24, 24);
      gc.setLineWidth(1);
      gc.drawRoundRectangle(innerX + 4, innerY + 4, innerW - 8, innerH - 8, 18, 18);

      if (style == CardStyle.CLASSIC_EVENT) {
        gc.setBackground(titleBarStart);
        gc.fillRoundRectangle(BORDER_WIDTH, BORDER_WIDTH, area.width - (BORDER_WIDTH * 2), TITLE_HEIGHT + 8, 20, 20);
      } else {
        Pattern titlePattern =
            new Pattern(
                getDisplay(),
                BORDER_WIDTH,
                BORDER_WIDTH,
                area.width - BORDER_WIDTH,
                BORDER_WIDTH + TITLE_HEIGHT,
                titleBarStart,
                titleBarEnd);
        gc.setBackgroundPattern(titlePattern);
        gc.fillRoundRectangle(BORDER_WIDTH, BORDER_WIDTH, area.width - (BORDER_WIDTH * 2), TITLE_HEIGHT + 8, 20, 20);
        gc.setBackgroundPattern(null);
        titlePattern.dispose();
      }

      drawTypeCircle(gc, 2, 2);
      drawTypeCircle(gc, area.width - TYPE_CIRCLE_SIZE - 2, 2);

      gc.setForeground(titleTextColor);
      drawAdaptiveTitle(gc, area);
    }

    private void disposeCachedSurface() {
      if (cachedSurface != null && !cachedSurface.isDisposed()) {
        cachedSurface.dispose();
      }
      cachedSurface = null;
      cachedSurfaceWidth = -1;
      cachedSurfaceHeight = -1;
    }

    private void drawTypeCircle(GC gc, int x, int y) {
      int size = TYPE_CIRCLE_SIZE;
      gc.setBackground(sealColor);
      gc.fillOval(x, y, size, size);
      gc.setForeground(frameColor);
      gc.drawOval(x, y, size, size);

      gc.setFont(circleFont);
      gc.setForeground(sealTextColor);
      Point textSize = gc.stringExtent(typeText);
      int tx = x + ((size - textSize.x) / 2);
      int ty = y + ((size - textSize.y) / 2);
      gc.drawText(typeText, tx, ty, true);
    }

    private void drawAdaptiveTitle(GC gc, Rectangle area) {
      if (isBlank(title)) {
        return;
      }

      int leftBound = 2 + TYPE_CIRCLE_SIZE + 10;
      int rightBound = area.width - TYPE_CIRCLE_SIZE - 2 - 10;
      int availableWidth = Math.max(10, rightBound - leftBound);
      int minSize = style == CardStyle.CLASSIC_EVENT ? 10 : 8;
      Font font = titleFont;
      Font resized = null;
      try {
        Point titleSize = measureText(gc, font, title);
        if (titleSize.x > availableWidth) {
          FontData base = titleFont.getFontData()[0];
          for (int size = base.getHeight() - 1; size >= minSize; size--) {
            Font candidate = new Font(getDisplay(), base.getName(), size, base.getStyle());
            Point candidateSize = measureText(gc, candidate, title);
            if (candidateSize.x <= availableWidth) {
              resized = candidate;
              font = candidate;
              titleSize = candidateSize;
              break;
            }
            candidate.dispose();
          }
          if (resized == null) {
            resized = new Font(getDisplay(), base.getName(), minSize, base.getStyle());
            font = resized;
            titleSize = measureText(gc, font, title);
          }
        }

        gc.setFont(font);
        int titleX = leftBound + ((availableWidth - titleSize.x) / 2);
        int titleY = BORDER_WIDTH + ((TITLE_HEIGHT - titleSize.y) / 2);
        gc.drawText(title, Math.max(leftBound, titleX), titleY, true);
      } finally {
        if (resized != null && !resized.isDisposed()) {
          resized.dispose();
        }
      }
    }

    private Point measureText(GC gc, Font font, String text) {
      gc.setFont(font);
      return gc.stringExtent(text);
    }
  }

  private static final class LinkEntry {
    private final String id;
    private final String text;

    private LinkEntry(String id, String text) {
      this.id = id;
      this.text = text;
    }
  }
}

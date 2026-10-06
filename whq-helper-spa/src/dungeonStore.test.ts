// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { DungeonCardStore } from './dungeonStore';

const FILES: Record<string, string> = {
  '/data/xml/dungeon/dungeon-cards.xml': `<dungeonCards>
    <card id="20" name="FIGHTING PIT" type="OBJECTIVE_ROOM" environment="The Old World" copyCount="1" enabled="true">
      <description>d</description><rules>r</rules><tileImagePath>t.png</tileImagePath>
    </card>
  </dungeonCards>`,
  '/data/xml/adventures/original-objective-room-adventures.xml': `<objectiveRoomAdventures>
    <objectiveRoom name="FIGHTING PIT" cardId="20">
      <adventure id="1" name="The Beast" generic="false"><flavor>f</flavor><rules>r</rules></adventure>
      <adventure id="2" name="Champion" generic="false"><flavor>f</flavor><rules>r</rules></adventure>
    </objectiveRoom>
  </objectiveRoomAdventures>`,
  '/data/i18n/content-es.xml': `<translations>
    <entry key="dungeonCard.20.name">FOSO DE COMBATE</entry>
  </translations>`
};

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe('loadAdventuresForObjectiveRoom', () => {
  it('finds the missions of a translated objective room by its card', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => (url in FILES ? new Response(FILES[url]) : new Response('', { status: 404 })))
    );
    const store = new DungeonCardStore();
    await store.init('ES');

    const [fightingPit] = store.loadObjectiveRoomsByEnvironment('The Old World');
    expect(fightingPit?.name).toBe('FOSO DE COMBATE');

    // El fallo: se buscaba por el nombre traducido y el XML trae el ingles; solo quedaba la generica.
    const missions = store.loadAdventuresForObjectiveRoom(fightingPit!);
    expect(missions.map((mission) => mission.id)).toEqual(['generic', '1', '2']);
    expect(missions.find((mission) => mission.generic)?.objectiveRoomName).toBe('FOSO DE COMBATE');
  });
});

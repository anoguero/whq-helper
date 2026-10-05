package com.whq.app.model;

import java.util.Optional;

import com.whq.app.i18n.Language;
import com.whq.app.storage.XmlRoomReferenceRepository;

import pms.whq.Settings;

/**
 * Fachada estatica sobre {@link XmlRoomReferenceRepository}: el contenido vive en
 * shared/data/xml/dungeon/room-references.xml y la busqueda es por id de carta.
 * Resuelve el shared home desde el directorio de Settings, como CardFactory.
 */
public final class WhiteDwarfRoomReferences {

    public record Reference(String source, String titleEn, String titleEs, String textEn, String textEs) {
        public String title(Language language) {
            return language == Language.EN ? titleEn : titleEs;
        }

        public String text(Language language) {
            return language == Language.EN ? textEn : textEs;
        }
    }

    private WhiteDwarfRoomReferences() {
    }

    public static Optional<Reference> find(DungeonCard card) {
        if (card == null) {
            return Optional.empty();
        }
        return find(card.getId());
    }

    /** Busca por id de carta en texto (p. ej. "56"). */
    public static Optional<Reference> find(String cardId) {
        if (cardId == null || cardId.isBlank()) {
            return Optional.empty();
        }
        try {
            return find(Long.parseLong(cardId.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public static Optional<Reference> find(long cardId) {
        return new XmlRoomReferenceRepository(Settings.getBaseDir()).find(cardId);
    }
}

package com.whq.app.game;

/**
 * Señala que el mazo de aventura no se puede construir con los parámetros dados.
 * Lleva la clave i18n del error, no el texto ya traducido: la traducción se
 * resuelve en la capa de UI (AppWindow), manteniendo esta clase libre de I18n.
 */
public class AdventureDeckException extends RuntimeException {

    private final String i18nKey;

    public AdventureDeckException(String i18nKey) {
        super(i18nKey);
        this.i18nKey = i18nKey;
    }

    public String i18nKey() {
        return i18nKey;
    }
}

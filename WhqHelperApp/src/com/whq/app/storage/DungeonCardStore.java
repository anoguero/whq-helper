package com.whq.app.storage;

import java.util.List;

import com.whq.app.model.DungeonCard;

public interface DungeonCardStore {
    List<DungeonCard> loadCards() throws DungeonCardStorageException;

    List<String> loadEnvironments() throws DungeonCardStorageException;

    List<DungeonCard> loadObjectiveRoomsByEnvironment(String environment) throws DungeonCardStorageException;

    void updateCard(DungeonCard card) throws DungeonCardStorageException;

    void updateCardAvailability(long cardId, int copyCount, boolean enabled) throws DungeonCardStorageException;

    void deleteCard(long cardId) throws DungeonCardStorageException;

    void insertCards(List<DungeonCard> cards) throws DungeonCardStorageException;
}

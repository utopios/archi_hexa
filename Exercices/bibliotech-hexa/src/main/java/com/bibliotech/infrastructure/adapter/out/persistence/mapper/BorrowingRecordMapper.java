package com.bibliotech.infrastructure.adapter.out.persistence.mapper;

import com.bibliotech.domain.model.BorrowingRecord;
import com.bibliotech.domain.vo.ISBN;
import com.bibliotech.domain.vo.MemberId;
import com.bibliotech.infrastructure.adapter.out.persistence.entity.BorrowingRecordJpaEntity;

public class BorrowingRecordMapper {

    private BorrowingRecordMapper() {}

    public static BorrowingRecord toDomain(BorrowingRecordJpaEntity entity) {
        BorrowingRecord record = BorrowingRecord.create(
                new MemberId(entity.getMemberId()),
                new ISBN(entity.getIsbn()),
                entity.getBorrowDate()
        );
        if (entity.isReturned()) {
            // On reconstitue l'etat depuis la persistance sans repasser par les methodes metier
            return new BorrowingRecord(
                    entity.getId(),
                    new MemberId(entity.getMemberId()),
                    new ISBN(entity.getIsbn()),
                    entity.getBorrowDate(),
                    entity.getReturnDate(),
                    entity.isReturned(),
                    entity.getPenalty()
            );
        }
        return record;
    }

    public static BorrowingRecordJpaEntity toJpa(BorrowingRecord domain) {
        BorrowingRecordJpaEntity entity = new BorrowingRecordJpaEntity();
        entity.setId(domain.getId());
        entity.setMemberId(domain.getMemberId().value());
        entity.setIsbn(domain.getIsbn().value());
        entity.setBorrowDate(domain.getBorrowDate());
        entity.setReturnDate(domain.getReturnDate());
        entity.setReturned(domain.isReturned());
        entity.setPenalty(domain.getPenalty().amount());
        return entity;
    }
}

package com.bibliotech.infrastructure.adapter.out.persistence.mapper;

import com.bibliotech.domain.model.Member;
import com.bibliotech.domain.vo.Email;
import com.bibliotech.domain.vo.MemberId;
import com.bibliotech.domain.vo.MemberName;
import com.bibliotech.domain.vo.Penalty;
import com.bibliotech.infrastructure.adapter.out.persistence.entity.MemberJpaEntity;

public class MemberMapper {

    private MemberMapper() {}

    public static Member toDomain(MemberJpaEntity entity) {
        return Member.reconstitute(
                new MemberId(entity.getMemberId()),
                new MemberName(entity.getName()),
                new Email(entity.getEmail()),
                entity.getBorrowedBooksCount(),
                new Penalty(entity.getUnpaidPenalties())
        );
    }

    public static MemberJpaEntity toJpa(Member domain) {
        MemberJpaEntity entity = new MemberJpaEntity();
        entity.setMemberId(domain.getMemberId().value());
        entity.setName(domain.getName().value());
        entity.setEmail(domain.getEmail().value());
        entity.setBorrowedBooksCount(domain.getBorrowedBooksCount());
        entity.setUnpaidPenalties(domain.getUnpaidPenalties().amount());
        return entity;
    }
}

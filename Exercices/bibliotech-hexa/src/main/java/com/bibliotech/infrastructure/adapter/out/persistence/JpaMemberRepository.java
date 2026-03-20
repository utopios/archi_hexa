package com.bibliotech.infrastructure.adapter.out.persistence;

import com.bibliotech.domain.model.Member;
import com.bibliotech.domain.port.out.MemberRepository;
import com.bibliotech.domain.vo.MemberId;
import com.bibliotech.infrastructure.adapter.out.persistence.entity.MemberJpaEntity;
import com.bibliotech.infrastructure.adapter.out.persistence.mapper.MemberMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class JpaMemberRepository implements MemberRepository {

    private final SpringDataMemberRepository springDataRepository;

    public JpaMemberRepository(SpringDataMemberRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<Member> findById(MemberId memberId) {
        return springDataRepository.findById(memberId.value())
                .map(MemberMapper::toDomain);
    }

    @Override
    public Member save(Member member) {
        MemberJpaEntity saved = springDataRepository.save(MemberMapper.toJpa(member));
        return MemberMapper.toDomain(saved);
    }

    @Override
    public List<Member> findAll() {
        return springDataRepository.findAll()
                .stream().map(MemberMapper::toDomain).toList();
    }
}

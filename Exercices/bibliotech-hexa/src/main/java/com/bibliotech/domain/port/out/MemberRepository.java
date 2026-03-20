package com.bibliotech.domain.port.out;

import com.bibliotech.domain.model.Member;
import com.bibliotech.domain.vo.MemberId;
import java.util.List;
import java.util.Optional;

public interface MemberRepository {
    Optional<Member> findById(MemberId memberId);
    Member save(Member member);
    List<Member> findAll();
}

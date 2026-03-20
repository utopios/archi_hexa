package com.bibliotech.infrastructure.adapter.out.persistence;

import com.bibliotech.domain.model.BorrowingRecord;
import com.bibliotech.domain.port.out.BorrowingRepository;
import com.bibliotech.domain.vo.ISBN;
import com.bibliotech.domain.vo.MemberId;
import com.bibliotech.infrastructure.adapter.out.persistence.entity.BorrowingRecordJpaEntity;
import com.bibliotech.infrastructure.adapter.out.persistence.mapper.BorrowingRecordMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class JpaBorrowingRepository implements BorrowingRepository {

    private final SpringDataBorrowingRepository springDataRepository;

    public JpaBorrowingRepository(SpringDataBorrowingRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<BorrowingRecord> findById(Long id) {
        return springDataRepository.findById(id)
                .map(BorrowingRecordMapper::toDomain);
    }

    @Override
    public List<BorrowingRecord> findActiveByMember(MemberId memberId) {
        return springDataRepository.findByMemberIdAndReturnedFalse(memberId.value())
                .stream().map(BorrowingRecordMapper::toDomain).toList();
    }

    @Override
    public List<BorrowingRecord> findActiveByBook(ISBN isbn) {
        return springDataRepository.findByIsbnAndReturnedFalse(isbn.value())
                .stream().map(BorrowingRecordMapper::toDomain).toList();
    }

    @Override
    public BorrowingRecord save(BorrowingRecord record) {
        BorrowingRecordJpaEntity saved = springDataRepository.save(BorrowingRecordMapper.toJpa(record));
        return BorrowingRecordMapper.toDomain(saved);
    }
}

package com.bibliotech.application.usecase;

import com.bibliotech.domain.event.DomainEvent;
import com.bibliotech.domain.model.Book;
import com.bibliotech.domain.model.BorrowingRecord;
import com.bibliotech.domain.model.Member;
import com.bibliotech.domain.port.in.BookLendingService;
import com.bibliotech.domain.port.out.BookRepository;
import com.bibliotech.domain.port.out.BorrowingRepository;
import com.bibliotech.domain.port.out.MemberRepository;
import com.bibliotech.domain.port.out.NotificationSender;
import com.bibliotech.domain.vo.Email;
import com.bibliotech.domain.vo.ISBN;
import com.bibliotech.domain.vo.MemberId;
import com.bibliotech.domain.vo.MemberName;
import com.bibliotech.domain.vo.Penalty;

import java.time.LocalDate;
import java.util.List;

public class BookLendingUseCase implements BookLendingService {

    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final BorrowingRepository borrowingRepository;
    private final NotificationSender notificationSender;

    public BookLendingUseCase(BookRepository bookRepository,
                               MemberRepository memberRepository,
                               BorrowingRepository borrowingRepository,
                               NotificationSender notificationSender) {
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.borrowingRepository = borrowingRepository;
        this.notificationSender = notificationSender;
    }

    @Override
    public BorrowingRecord borrowBook(String memberIdStr, String isbnStr) {
        MemberId memberId = new MemberId(memberIdStr);
        ISBN isbn = new ISBN(isbnStr);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Membre non trouve: " + memberIdStr));

        Book book = bookRepository.findByIsbn(isbn)
                .orElseThrow(() -> new IllegalArgumentException("Livre non trouve: " + isbnStr));

        member.borrow();
        book.decrementStock();

        BorrowingRecord record = BorrowingRecord.create(memberId, isbn, LocalDate.now());

        memberRepository.save(member);
        bookRepository.save(book);
        BorrowingRecord savedRecord = borrowingRepository.save(record);

        List<DomainEvent> events = savedRecord.pullDomainEvents();
        events.forEach(notificationSender::notifyEvent);

        return savedRecord;
    }

    @Override
    public BorrowingRecord returnBook(Long borrowingId) {
        BorrowingRecord record = borrowingRepository.findById(borrowingId)
                .orElseThrow(() -> new IllegalArgumentException("Emprunt non trouve: " + borrowingId));

        Member member = memberRepository.findById(record.getMemberId())
                .orElseThrow(() -> new IllegalStateException("Membre introuvable"));

        Book book = bookRepository.findByIsbn(record.getIsbn())
                .orElseThrow(() -> new IllegalStateException("Livre introuvable"));

        Penalty penalty = record.markAsReturned(LocalDate.now());
        member.returnBook(penalty);
        book.incrementStock();

        memberRepository.save(member);
        bookRepository.save(book);
        borrowingRepository.save(record);

        List<DomainEvent> events = record.pullDomainEvents();
        events.forEach(notificationSender::notifyEvent);

        return record;
    }

    @Override
    public void payPenalty(String memberIdStr, double amount) {
        MemberId memberId = new MemberId(memberIdStr);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Membre non trouve: " + memberIdStr));
        member.payPenalty(amount);
        memberRepository.save(member);
    }

    @Override
    public Member registerMember(String name, String email) {
        MemberId memberId = MemberId.generate();
        Member member = Member.register(memberId, new MemberName(name), new Email(email));
        return memberRepository.save(member);
    }

    @Override
    public Member findMember(String memberIdStr) {
        return memberRepository.findById(new MemberId(memberIdStr))
                .orElseThrow(() -> new IllegalArgumentException("Membre non trouve: " + memberIdStr));
    }

    @Override
    public List<Member> findAllMembers() {
        return memberRepository.findAll();
    }
}

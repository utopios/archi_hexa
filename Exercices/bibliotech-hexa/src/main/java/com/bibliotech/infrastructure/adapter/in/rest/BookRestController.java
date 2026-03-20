package com.bibliotech.infrastructure.adapter.in.rest;

import com.bibliotech.domain.model.BorrowingRecord;
import com.bibliotech.domain.model.Member;
import com.bibliotech.domain.port.in.BookCatalogService;
import com.bibliotech.domain.port.in.BookLendingService;
import com.bibliotech.infrastructure.adapter.in.rest.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BookRestController {

    private final BookCatalogService catalogService;
    private final BookLendingService lendingService;

    public BookRestController(BookCatalogService catalogService,
                              BookLendingService lendingService) {
        this.catalogService = catalogService;
        this.lendingService = lendingService;
    }

    // ---- Livres ----

    @PostMapping("/books")
    public ResponseEntity<BookResponse> addBook(@RequestBody BookResponse request) {
        return ResponseEntity.ok(BookResponse.from(
                catalogService.addBook(request.isbn(), request.title(), request.author(), request.availableCopies())));
    }

    @GetMapping("/books/{isbn}")
    public ResponseEntity<BookResponse> getBook(@PathVariable String isbn) {
        return catalogService.findByIsbn(isbn)
                .map(BookResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/books")
    public ResponseEntity<List<BookResponse>> getAllBooks() {
        return ResponseEntity.ok(catalogService.findAll().stream().map(BookResponse::from).toList());
    }

    @GetMapping("/books/search")
    public ResponseEntity<List<BookResponse>> searchBooks(@RequestParam String keyword) {
        return ResponseEntity.ok(catalogService.searchByTitle(keyword).stream().map(BookResponse::from).toList());
    }

    @DeleteMapping("/books/{isbn}")
    public ResponseEntity<Void> deleteBook(@PathVariable String isbn) {
        catalogService.removeBook(isbn);
        return ResponseEntity.noContent().build();
    }

    // ---- Membres ----

    @PostMapping("/members")
    public ResponseEntity<MemberResponse> registerMember(@RequestBody RegisterMemberRequest request) {
        Member member = lendingService.registerMember(request.name(), request.email());
        return ResponseEntity.ok(MemberResponse.from(member));
    }

    @GetMapping("/members/{memberId}")
    public ResponseEntity<MemberResponse> getMember(@PathVariable String memberId) {
        return ResponseEntity.ok(MemberResponse.from(lendingService.findMember(memberId)));
    }

    @GetMapping("/members")
    public ResponseEntity<List<MemberResponse>> getAllMembers() {
        return ResponseEntity.ok(lendingService.findAllMembers().stream().map(MemberResponse::from).toList());
    }

    // ---- Emprunts ----

    @PostMapping("/borrow")
    public ResponseEntity<BorrowingRecordResponse> borrowBook(
            @RequestParam String memberId, @RequestParam String isbn) {
        BorrowingRecord record = lendingService.borrowBook(memberId, isbn);
        return ResponseEntity.ok(BorrowingRecordResponse.from(record));
    }

    @PostMapping("/return/{borrowingId}")
    public ResponseEntity<BorrowingRecordResponse> returnBook(@PathVariable Long borrowingId) {
        BorrowingRecord record = lendingService.returnBook(borrowingId);
        return ResponseEntity.ok(BorrowingRecordResponse.from(record));
    }

    @PostMapping("/members/{memberId}/pay")
    public ResponseEntity<Void> payPenalty(
            @PathVariable String memberId, @RequestParam double amount) {
        lendingService.payPenalty(memberId, amount);
        return ResponseEntity.ok().build();
    }

    // ---- Gestion des erreurs ----

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleException(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}

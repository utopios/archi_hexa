package com.bibliotech.controller;

import com.bibliotech.model.*;
import com.bibliotech.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BookController {

    @Autowired
    private BookService bookService;

    // ---- Livres ----

    @PostMapping("/books")
    public ResponseEntity<Book> addBook(@RequestBody Book book) {
        return ResponseEntity.ok(bookService.addBook(book));
    }

    @GetMapping("/books/{isbn}")
    public ResponseEntity<Book> getBook(@PathVariable String isbn) {
        return ResponseEntity.ok(bookService.getBook(isbn));
    }

    @GetMapping("/books")
    public ResponseEntity<List<Book>> getAllBooks() {
        return ResponseEntity.ok(bookService.getAllBooks());
    }

    @GetMapping("/books/search")
    public ResponseEntity<List<Book>> searchBooks(@RequestParam String keyword) {
        return ResponseEntity.ok(bookService.searchBooks(keyword));
    }

    @DeleteMapping("/books/{isbn}")
    public ResponseEntity<Void> deleteBook(@PathVariable String isbn) {
        bookService.deleteBook(isbn);
        return ResponseEntity.noContent().build();
    }

    // ---- Membres ----

    @PostMapping("/members")
    public ResponseEntity<Member> registerMember(@RequestBody Member member) {
        return ResponseEntity.ok(bookService.registerMember(member));
    }

    @GetMapping("/members/{memberId}")
    public ResponseEntity<Member> getMember(@PathVariable String memberId) {
        return ResponseEntity.ok(bookService.getMember(memberId));
    }

    @GetMapping("/members")
    public ResponseEntity<List<Member>> getAllMembers() {
        return ResponseEntity.ok(bookService.getAllMembers());
    }

    // ---- Emprunts ----

    @PostMapping("/borrow")
    public ResponseEntity<BorrowingRecord> borrowBook(
            @RequestParam String memberId,
            @RequestParam String isbn) {
        return ResponseEntity.ok(bookService.borrowBook(memberId, isbn));
    }

    @PostMapping("/return/{borrowingId}")
    public ResponseEntity<BorrowingRecord> returnBook(@PathVariable Long borrowingId) {
        return ResponseEntity.ok(bookService.returnBook(borrowingId));
    }

    @PostMapping("/members/{memberId}/pay")
    public ResponseEntity<Void> payPenalty(
            @PathVariable String memberId,
            @RequestParam double amount) {
        bookService.payPenalty(memberId, amount);
        return ResponseEntity.ok().build();
    }

    // ---- Gestion des erreurs ----

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleException(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}

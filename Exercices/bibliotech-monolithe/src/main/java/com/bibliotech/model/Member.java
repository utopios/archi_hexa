package com.bibliotech.model;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String memberId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private int borrowedBooksCount;

    @Column(nullable = false)
    private double unpaidPenalties;

    public Member() {}

    public Member(String name, String email) {
        this.name = name;
        this.email = email;
        this.borrowedBooksCount = 0;
        this.unpaidPenalties = 0.0;
    }

    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getBorrowedBooksCount() { return borrowedBooksCount; }
    public void setBorrowedBooksCount(int borrowedBooksCount) {
        this.borrowedBooksCount = borrowedBooksCount;
    }

    public double getUnpaidPenalties() { return unpaidPenalties; }
    public void setUnpaidPenalties(double unpaidPenalties) {
        this.unpaidPenalties = unpaidPenalties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Member member = (Member) o;
        return Objects.equals(memberId, member.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId);
    }

    @Override
    public String toString() {
        return "Member{memberId='" + memberId + "', name='" + name + "', email='" + email + "', borrowedBooksCount=" + borrowedBooksCount + ", unpaidPenalties=" + unpaidPenalties + "}";
    }
}

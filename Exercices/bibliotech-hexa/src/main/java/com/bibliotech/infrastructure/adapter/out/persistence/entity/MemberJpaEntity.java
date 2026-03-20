package com.bibliotech.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "members")
public class MemberJpaEntity {

    @Id
    private String memberId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private int borrowedBooksCount;

    @Column(nullable = false)
    private double unpaidPenalties;

    public MemberJpaEntity() {}

    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public int getBorrowedBooksCount() { return borrowedBooksCount; }
    public void setBorrowedBooksCount(int c) { this.borrowedBooksCount = c; }
    public double getUnpaidPenalties() { return unpaidPenalties; }
    public void setUnpaidPenalties(double p) { this.unpaidPenalties = p; }
}

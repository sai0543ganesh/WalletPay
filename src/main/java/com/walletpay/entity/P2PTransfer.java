package com.walletpay.entity;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="p2p_transfers") public class P2PTransfer {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(nullable=false) private long amount; @Column(nullable=false) private Instant timestamp;
 @ManyToOne(optional=false) @JoinColumn(name="from_user_id") private User fromUser; @ManyToOne(optional=false) @JoinColumn(name="to_user_id") private User toUser;
 public Long getId(){return id;} public long getAmount(){return amount;} public void setAmount(long v){amount=v;} public Instant getTimestamp(){return timestamp;} public void setTimestamp(Instant v){timestamp=v;} public User getFromUser(){return fromUser;} public void setFromUser(User v){fromUser=v;} public User getToUser(){return toUser;} public void setToUser(User v){toUser=v;}
}
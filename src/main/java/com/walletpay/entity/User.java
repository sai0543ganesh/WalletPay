package com.walletpay.entity;
import jakarta.persistence.*;
@Entity @Table(name="users")
public class User {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(unique=true) private String email; private String name;
 @Column(nullable=false,unique=true) private String number;
 @Column(nullable=false) private String password;
 public Long getId(){return id;} public String getEmail(){return email;} public void setEmail(String v){email=v;} public String getName(){return name;} public void setName(String v){name=v;} public String getNumber(){return number;} public void setNumber(String v){number=v;} public String getPassword(){return password;} public void setPassword(String v){password=v;}
}
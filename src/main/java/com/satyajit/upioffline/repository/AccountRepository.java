package com.satyajit.upioffline.repository;

import com.satyajit.upioffline.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;


public interface AccountRepository extends JpaRepository<Account,String> {
}

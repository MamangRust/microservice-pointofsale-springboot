package com.cashier.cashier.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cashier.cashier.entity.Cashier;

public interface CashierRepository extends JpaRepository<Cashier, Long> {
    List<Cashier> findByMerchantId(Long merchantId);
}

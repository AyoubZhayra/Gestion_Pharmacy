package com.pharmacy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.pharmacy.model.SaleItem;

@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
} 
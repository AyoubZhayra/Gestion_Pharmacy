package com.pharmacy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.pharmacy.model.Sale;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {
} 
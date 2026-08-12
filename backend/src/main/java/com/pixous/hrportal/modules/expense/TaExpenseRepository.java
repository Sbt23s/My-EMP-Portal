package com.pixous.hrportal.modules.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaExpenseRepository extends JpaRepository<TaExpense, Long> {
    List<TaExpense> findByUserIdOrderByDateDesc(Long userId);
}

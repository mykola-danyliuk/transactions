package mykola.danyliuk.transactions;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<TransactionModel, Long> {
    Optional<TransactionModel> findByHash(String hash);
    List<TransactionModel> findByBlockNumber(Long blockNumber);
    @Query("SELECT MAX(t.blockNumber) FROM TransactionModel t")
    Optional<Long> maxBlockNumber();
    @Modifying
    int deleteByBlockNumberLessThan(Long blockNumberLimit);
}
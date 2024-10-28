package mykola.danyliuk.transactions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionArchiveRepository extends JpaRepository<TransactionArchiveModel, Long> {
    Optional<TransactionArchiveModel> findByHash(String hash);
    List<TransactionArchiveModel> findByBlockNumber(Long blockNumber);
}
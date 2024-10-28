package mykola.danyliuk.transactions;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface TransactionESRepository extends ElasticsearchRepository<TransactionModel, String> {
}
package mykola.danyliuk.transactions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions")
@Value
@NoArgsConstructor(force = true)
@AllArgsConstructor
@Builder
public class TransactionModel implements TransactionBaseModel {

    @Id
    String hash;
    @Column(name = "from_address")
    String from;
    @Column(name = "to_address")
    String to;
    String value;
    String gasPrice;
    String gas;
    String input;
    Long blockNumber;
    String transactionIndex;
    String fts;

}
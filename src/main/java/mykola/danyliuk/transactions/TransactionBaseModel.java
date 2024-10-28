package mykola.danyliuk.transactions;

public interface TransactionBaseModel {
    String getHash();
    String getFrom();
    String getTo();
    String getValue();
    String getGasPrice();
    String getGas();
    String getInput();
    Long getBlockNumber();
    String getTransactionIndex();
}

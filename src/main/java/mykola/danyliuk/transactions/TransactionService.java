package mykola.danyliuk.transactions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.http.HttpService;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Character.SPACE_SEPARATOR;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final int CACHE_SIZE = 10;
    private static final int DB_SIZE = 1000;
    private static final String INFURA_BASIC_URL = "https://mainnet.infura.io/v3/";

    private final TransactionRepository transactionRepository;
    private final TransactionArchiveRepository transactionArchiveRepository;
    private final TransactionESRepository transactionESRepository;


    @Value("${infura.api.key}")
    private String infuraApiKey;

    private Web3j web3j;

    private final ConcurrentHashMap<String, TransactionModel> transactionCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<TransactionModel>> blockCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        web3j = Web3j.build(new HttpService(INFURA_BASIC_URL + infuraApiKey));
        startTransactionListener();
    }

    //////////////////////
    // WRITE operations //
    //////////////////////

    // Start listening for new transactions from the blockchain
    public void startTransactionListener() {
        web3j.transactionFlowable().subscribe(tx -> {
            //log.info("New transaction: {}", tx.getHash());
            saveTransaction(tx);
        });
    }

    // Save the transaction to the database
    public void saveTransaction(Transaction tx) {
        Optional<TransactionModel> existingTransaction = transactionRepository.findByHash(tx.getHash());
        if (existingTransaction.isEmpty()) {
            long blockNumber = tx.getBlockNumber().longValue();
            String fts = new StringBuilder(tx.getFrom()).append(SPACE_SEPARATOR)
                .append(tx.getTo()).append(SPACE_SEPARATOR)
                .append(tx.getInput()).append(SPACE_SEPARATOR)
                .append(tx.getHash()).append(SPACE_SEPARATOR)
                .append(blockNumber).toString();
            TransactionModel model = TransactionModel.builder()
                .hash(tx.getHash())
                .from(tx.getFrom())
                .to(tx.getTo())
                .value(tx.getValueRaw())
                .gasPrice(tx.getGasPriceRaw())
                .gas(tx.getGasRaw())
                .input(tx.getInput())
                .blockNumber(blockNumber)
                .transactionIndex(tx.getTransactionIndexRaw())
                .fts(fts)
                .build();
            TransactionArchiveModel archiveModel = TransactionArchiveModel.builder()
                .hash(tx.getHash())
                .from(tx.getFrom())
                .to(tx.getTo())
                .value(tx.getValueRaw())
                .gasPrice(tx.getGasPriceRaw())
                .gas(tx.getGasRaw())
                .input(tx.getInput())
                .blockNumber(blockNumber)
                .transactionIndex(tx.getTransactionIndexRaw())
                .fts(fts)
                .build();
            transactionRepository.save(model);
            transactionArchiveRepository.save(archiveModel);
            writeIntoCachesAndES(model);
        }
    }

    @Async
    protected void writeIntoCachesAndES(TransactionModel model) {
        transactionESRepository.save(model);
        transactionCache.put(model.getHash(), model);
        var value = blockCache.get(model.getBlockNumber());
        if (value != null) {
            value.add(model);
        } else {
            value = new ArrayList<>();
            value.add(model);
            blockCache.put(model.getBlockNumber(), value);
        }
    }

    @Transactional
    @Scheduled(fixedRate = 60_000) // Runs every 60 seconds
    public void scheduledCacheCleanup() {
        log.info("Starting scheduled cache cleanup, current cache size: {}", transactionCache.size());
        long maxBlockNumberLimit = transactionRepository.maxBlockNumber().orElseThrow() - CACHE_SIZE;
        transactionCache.entrySet().removeIf(entry -> entry.getValue().getBlockNumber() < maxBlockNumberLimit);
        blockCache.entrySet().removeIf(entry -> entry.getKey() < maxBlockNumberLimit);
        log.info("Cache cleanup finished, current cache size: {}", transactionCache.size());
    }

    @Transactional
    @Scheduled(fixedRate = 10 * 60_000) // Runs every 10 minutes
    public void scheduledDBCleanup() {
        // todo consider using pg_cron
        // todo After deletions, it’s important to run VACUUM on the main table to reclaim space and optimize performance.
        log.info("Starting scheduled DB cleanup");
        Long maxBlockNumberLimit = transactionRepository.maxBlockNumber().orElseThrow() - DB_SIZE;
        int deleted = transactionRepository.deleteByBlockNumberLessThan(maxBlockNumberLimit);
        log.info("Deleted {} transactions", deleted);
    }

    /////////////////////
    // READ operations //
    /////////////////////

    // Fetch a transaction by its hash
    public Optional<? extends TransactionBaseModel> getTransactionByHash(String hash) {
        var o = Optional.ofNullable(transactionCache.get(hash))
            .or(() -> transactionRepository.findByHash(hash));
        if (o.isPresent()) {
            return o;
        } else {
            return transactionArchiveRepository.findByHash(hash);
        }
    }

    // Fetch transactions by block number
    public List<? extends TransactionBaseModel> getTransactionsByBlockNumber(Long blockNumber) {
        var list = blockCache.get(blockNumber);
        if (list != null && !list.isEmpty()) {
            log.info("Returning transactions from cache, blockNumber: {}", blockNumber);
            return list;
        }
        list = transactionRepository.findByBlockNumber(blockNumber);
        if (!list.isEmpty()) {
            log.info("Returning transactions from DB, blockNumber: {}", blockNumber);
            return list;
        }
        return transactionArchiveRepository.findByBlockNumber(blockNumber);
    }

    public List<TransactionModel> searchTransactions(String query) {
        return Collections.emptyList();
    }


}
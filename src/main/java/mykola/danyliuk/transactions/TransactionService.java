package mykola.danyliuk.transactions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.http.HttpService;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// todo add application metrics in next version
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final int CACHE_SIZE = 10; // how many blocks to keep in cache
    private static final int DB_SIZE = 1000; // how many blocks to keep in main DB
    private static final String INFURA_BASIC_URL = "https://mainnet.infura.io/v3/";

    private final TransactionRepository transactionRepository;
    private final TransactionArchiveRepository transactionArchiveRepository;

    @Value("${infura.api.key}")
    private String infuraApiKey;
    private Web3j web3j;

    // todo consider and benchmark using other caching solutions in next version
    private final ConcurrentHashMap<String, TransactionModel> transactionCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<TransactionModel>> blockCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            web3j = Web3j.build(new HttpService(INFURA_BASIC_URL + infuraApiKey));
            startTransactionListener();
        } catch (Exception e) {
            log.error("Failed to initialize Web3j client for transaction listener", e);
        }
    }

    //////////////////////
    // WRITE operations //
    //////////////////////

    public void startTransactionListener() {
        web3j.transactionFlowable().subscribe(this::saveTransaction, error -> log.error("Error in transaction listener", error));
    }

    public void saveTransaction(Transaction tx) {
        Optional<TransactionModel> existingTransaction = transactionRepository.findByHash(tx.getHash());
        if (existingTransaction.isEmpty()) {
            long blockNumber = tx.getBlockNumber().longValue();
            String fts = buildFts(tx, blockNumber);
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
            writeIntoCaches(model);
        }
    }

    private String buildFts(Transaction tx, long blockNumber) {
        return String.format("%s %s %s %s %d",
            tx.getFrom(),
            tx.getTo(),
            tx.getInput(),
            tx.getHash(),
            blockNumber);
    }

    @Async
    protected void writeIntoCaches(TransactionModel model) {
        transactionCache.put(model.getHash(), model);
        blockCache.computeIfAbsent(model.getBlockNumber(), k -> new ArrayList<>()).add(model);
    }

    @Transactional
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void scheduledCacheCleanup() {
        log.info("Starting scheduled cache cleanup, current cache size: {}", transactionCache.size());
        try {
            long maxBlockNumberLimit = transactionRepository.maxBlockNumber().orElse(Long.MAX_VALUE) - CACHE_SIZE;
            transactionCache.entrySet().removeIf(entry -> entry.getValue().getBlockNumber() < maxBlockNumberLimit);
            blockCache.entrySet().removeIf(entry -> entry.getKey() < maxBlockNumberLimit);
        } catch (Exception e) {
            log.error("Error during cache cleanup", e);
        }
        log.info("Cache cleanup finished, current cache size: {}", transactionCache.size());
    }

    // todo rewrite with using pg_cron and vacuum in next version
    @Transactional
    @Scheduled(fixedRate = 60, timeUnit = TimeUnit.MINUTES)
    public void scheduledDBCleanup() {
        log.info("Starting scheduled DB cleanup");
        Long maxBlockNumberLimit = transactionRepository.maxBlockNumber().orElse(Long.MAX_VALUE) - DB_SIZE;
        int deleted = transactionRepository.deleteByBlockNumberLessThan(maxBlockNumberLimit);
        log.info("Deleted {} transactions", deleted);
    }

    /////////////////////
    // READ operations //
    /////////////////////

    public Optional<? extends TransactionBaseModel> getTransactionByHash(String hash) {
        var o = Optional.ofNullable(transactionCache.get(hash))
            .or(() -> transactionRepository.findByHash(hash));
        if (o.isPresent()) {
            return o;
        } else {
            return transactionArchiveRepository.findByHash(hash);
        }
    }

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

    // todo rewrite with using elasticsearch in next version
    public Page<? extends TransactionBaseModel> fullTextSearch(String query, int page, int size) {
        return transactionArchiveRepository.findByFtsLikeIgnoreCase("%" + query + "%", PageRequest.of(page, size));
    }


}
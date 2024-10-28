package mykola.danyliuk.transactions;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping("/tx/{hash}")
    public Optional<? extends TransactionBaseModel> getAllTransactions(@PathVariable String hash) {
        return transactionService.getTransactionByHash(hash);
    }

    @GetMapping("/block/{blockNumber}")
    public List<? extends TransactionBaseModel> getTransactionsByBlockNumber(@PathVariable Long blockNumber) {
        return transactionService.getTransactionsByBlockNumber(blockNumber);
    }

}
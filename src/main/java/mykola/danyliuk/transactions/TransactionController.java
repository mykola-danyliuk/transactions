package mykola.danyliuk.transactions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Transaction Controller", description = "APIs for managing transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(summary = "Get transaction by hash", description = "Retrieve a transaction using its hash")
    @GetMapping("/tx/{hash}")
    public Optional<? extends TransactionBaseModel> getTransactionByHash(@PathVariable String hash) {
        return transactionService.getTransactionByHash(hash);
    }

    @Operation(summary = "Get transactions by block number", description = "Retrieve transactions using the block number")
    @GetMapping("/block/{blockNumber}")
    public List<? extends TransactionBaseModel> getTransactionsByBlockNumber(@PathVariable Long blockNumber) {
        return transactionService.getTransactionsByBlockNumber(blockNumber);
    }

    @Operation(summary = "Full text search transactions", description = "Search transactions using a full text search query")
    @GetMapping("/fts/{query}")
    public Page<? extends TransactionBaseModel> getTransactionsByFtsQuery(
        @PathVariable String query,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "100") int size) {
        return transactionService.fullTextSearch(query, page, size);
    }

}
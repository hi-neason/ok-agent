package io.okagent.service.product;

import io.okagent.domain.product.Solution;
import io.okagent.domain.product.SolutionItem;
import io.okagent.domain.product.SolutionItemRole;
import io.okagent.domain.product.SolutionStatus;
import io.okagent.repository.product.ProductRepository;
import io.okagent.repository.product.SolutionItemRepository;
import io.okagent.repository.product.SolutionRepository;
import io.okagent.web.product.SolutionItemRequest;
import io.okagent.web.product.SolutionRequest;
import io.okagent.web.product.SolutionResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Service
public class SolutionService {
    private final SolutionRepository solutions;
    private final SolutionItemRepository items;
    private final ProductRepository products;

    public SolutionService(
            SolutionRepository solutions, SolutionItemRepository items, ProductRepository products) {
        this.solutions = solutions;
        this.items = items;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public Page<SolutionResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return solutions.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SolutionResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public SolutionResponse create(SolutionRequest request) {
        validate(request);
        if (solutions.existsBySolutionKey(request.solutionKey())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "solutionKey already exists: " + request.solutionKey());
        }
        var solution = new Solution(UUID.randomUUID(), request.solutionKey().trim(), request.name().trim());
        apply(solution, request);
        solution = solutions.save(solution);
        replaceItems(solution.getId(), request.items());
        return toResponse(solutions.findById(solution.getId()).orElseThrow());
    }

    @Transactional
    public SolutionResponse update(UUID id, SolutionRequest request) {
        validate(request);
        var solution = require(id);
        if (!solution.getSolutionKey().equals(request.solutionKey())
                && solutions.existsBySolutionKey(request.solutionKey())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "solutionKey already exists: " + request.solutionKey());
        }
        apply(solution, request);
        solutions.save(solution);
        replaceItems(solution.getId(), request.items());
        return toResponse(solutions.findById(id).orElseThrow());
    }

    @Transactional
    public SolutionResponse setStatus(UUID id, SolutionStatus status) {
        var solution = require(id);
        solution.setStatus(status);
        solutions.save(solution);
        return toResponse(solution);
    }

    @Transactional
    public void delete(UUID id) {
        solutions.deleteById(id);
    }

    private void apply(Solution solution, SolutionRequest request) {
        solution.apply(
                request.name() == null ? solution.getName() : request.name().trim(),
                request.description(),
                request.targetCustomer(),
                request.scenario(),
                request.priceNote(),
                request.status());
    }

    private void replaceItems(UUID solutionId, List<SolutionItemRequest> requested) {
        items.deleteBySolutionId(solutionId);
        if (requested == null || requested.isEmpty()) return;
        int order = 0;
        for (SolutionItemRequest req : requested) {
            if (req.productId() == null) continue;
            if (!products.existsById(req.productId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Solution item product not found: " + req.productId());
            }
            SolutionItemRole role = req.role() == null ? SolutionItemRole.PRIMARY : req.role();
            items.save(new SolutionItem(
                    UUID.randomUUID(),
                    solutionId,
                    req.productId(),
                    req.quantity() == null || req.quantity() <= 0 ? 1 : req.quantity(),
                    role,
                    req.sortOrder() == null ? order : req.sortOrder()));
            order++;
        }
    }

    private Solution require(UUID id) {
        return solutions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solution not found: " + id));
    }

    private void validate(SolutionRequest request) {
        if (request.solutionKey() == null || request.solutionKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "solutionKey is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
    }

    private SolutionResponse toResponse(Solution solution) {
        List<SolutionItem> rows = items.findBySolutionIdOrderBySortOrderAsc(solution.getId());
        Map<UUID, String> productNames = new HashMap<>();
        Map<UUID, String> productKeys = new HashMap<>();
        products.findAllByIdIn(rows.stream().map(SolutionItem::getProductId).toList()).forEach(p -> {
            productNames.put(p.getId(), p.getName());
            productKeys.put(p.getId(), p.getProductKey());
        });
        List<SolutionResponse.Item> itemViews = new ArrayList<>();
        for (SolutionItem row : rows) {
            itemViews.add(new SolutionResponse.Item(
                    row.getId(),
                    row.getProductId(),
                    productKeys.getOrDefault(row.getProductId(), ""),
                    productNames.getOrDefault(row.getProductId(), "(missing)"),
                    row.getQuantity(),
                    row.getRole(),
                    row.getSortOrder()));
        }
        return new SolutionResponse(
                solution.getId(),
                solution.getSolutionKey(),
                solution.getName(),
                solution.getDescription(),
                solution.getTargetCustomer(),
                solution.getScenario(),
                solution.getPriceNote(),
                solution.getStatus(),
                solution.getVersion(),
                solution.getCreatedAt(),
                solution.getUpdatedAt(),
                itemViews);
    }
}

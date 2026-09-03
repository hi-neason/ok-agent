package io.okagent.module.product.application;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The read-only tools exposed to the LLM for product/solution discovery and recommendation. One
 * instance is built per agent (holding its agentId), so the catalog authorizes every call against
 * that agent's binding. Tools are gated by the binding's capabilities: customer-service agents
 * typically only get QUERY (search/get), sales agents additionally get RECOMMEND and SOLUTION.
 */
public class ProductTools {
    private static final Logger log = LoggerFactory.getLogger(ProductTools.class);

    private final ProductRuntimeCatalog catalog;
    private final SolutionRuntimeCatalog solutionCatalog;
    private final UUID agentId;
    private final Set<String> capabilities;

    public ProductTools(
            ProductRuntimeCatalog catalog,
            SolutionRuntimeCatalog solutionCatalog,
            UUID agentId,
            Set<String> capabilities) {
        this.catalog = catalog;
        this.solutionCatalog = solutionCatalog;
        this.agentId = agentId;
        this.capabilities = capabilities;
    }

    @Tool(
            name = "search_products",
            readOnly = true,
            description =
                    "Search the product catalog by keyword and optional filters. Returns visible active"
                            + " products with id, name, brand, category, price band, scenario tags and selling"
                            + " points. Use this to find products and then call get_product for full details."
                            + " Pass the user's natural-language need as query.")
    public String searchProducts(
            RuntimeContext ctx,
            @ToolParam(name = "query", description = "Natural-language keyword query") String query,
            @ToolParam(
                            name = "category",
                            description = "Optional exact category filter",
                            required = false)
                    String category,
            @ToolParam(name = "minPrice", description = "Optional minimum price", required = false)
                    Double minPrice,
            @ToolParam(name = "maxPrice", description = "Optional maximum price", required = false)
                    Double maxPrice,
            @ToolParam(
                            name = "topK",
                            description = "Maximum products to return (1-50); omit for the default of 10",
                            required = false)
                    Integer topK) {
        if (!capabilities.contains("QUERY")) {
            return "Product search is not enabled for this agent.";
        }
        try {
            List<RankedProduct> products = catalog.recommend(
                    agentId,
                    query,
                    category,
                    minPrice == null ? null : BigDecimal.valueOf(minPrice),
                    maxPrice == null ? null : BigDecimal.valueOf(maxPrice),
                    null,
                    topK == null ? 10 : topK);
            return renderProducts(products);
        } catch (Exception e) {
            log.warn("search_products failed for agent {}: {}", agentId, e.getMessage(), e);
            return "Error searching products: " + e.getMessage();
        }
    }

    @Tool(
            name = "recommend_products",
            readOnly = true,
            description =
                    "Recommend products for a customer requirement. Provide the customer's budget and"
                            + " scenario tags; the catalog runs deterministic filters and weighted ranking"
                            + " (budget fit, tag match, relevance) and returns a ranked shortlist. Then YOU"
                            + " select 1-3 and explain why each fits, citing prices and selling points."
                            + " Requires the RECOMMEND capability (typically sales agents).")
    public String recommendProducts(
            RuntimeContext ctx,
            @ToolParam(
                            name = "requirement",
                            description =
                                    "Natural-language description of the customer's need (used for keyword"
                                            + " relevance ranking)")
                    String requirement,
            @ToolParam(
                            name = "minPrice",
                            description = "Customer's minimum budget",
                            required = false)
                    Double minPrice,
            @ToolParam(
                            name = "maxPrice",
                            description = "Customer's maximum budget",
                            required = false)
                    Double maxPrice,
            @ToolParam(
                            name = "tags",
                            description =
                                    "Scenario/requirement tags to match against product scenario_tags, e.g."
                                            + " [\"small-business\",\"self-hosted\"]",
                            required = false)
                    List<String> tags,
            @ToolParam(
                            name = "category",
                            description = "Optional exact category filter",
                            required = false)
                    String category,
            @ToolParam(
                            name = "topK",
                            description = "Maximum candidates to return (1-50); omit for 10",
                            required = false)
                    Integer topK) {
        if (!capabilities.contains("RECOMMEND")) {
            return "Product recommendation is not enabled for this agent.";
        }
        try {
            List<RankedProduct> products = catalog.recommend(
                    agentId,
                    requirement,
                    category,
                    minPrice == null ? null : BigDecimal.valueOf(minPrice),
                    maxPrice == null ? null : BigDecimal.valueOf(maxPrice),
                    tags,
                    topK == null ? 10 : topK);
            if (products.isEmpty()) {
                return "No products match the given budget, category and tags.";
            }
            var sb = new StringBuilder("Ranked candidates (score = budget fit + tag match + relevance):\n");
            for (int i = 0; i < products.size(); i++) {
                RankedProduct p = products.get(i);
                sb.append(i + 1)
                        .append(". id=")
                        .append(p.productKey())
                        .append("  [score ")
                        .append(String.format("%.1f", p.score()))
                        .append("]\n   ")
                        .append(p.name())
                        .append(" — ")
                        .append(renderPrice(p))
                        .append('\n');
                if (p.sellingPoints() != null && !p.sellingPoints().isBlank()) {
                    sb.append("   卖点: ").append(firstLine(p.sellingPoints())).append('\n');
                }
                if (!p.scenarioTags().isEmpty()) {
                    sb.append("   场景标签: ").append(String.join(", ", p.scenarioTags())).append('\n');
                }
            }
            sb.append("\nSelect the best 1-3 for this customer and explain your reasoning. Call"
                    + " get_product for full specs/details before quoting.");
            return sb.toString();
        } catch (Exception e) {
            log.warn("recommend_products failed for agent {}: {}", agentId, e.getMessage(), e);
            return "Error recommending products: " + e.getMessage();
        }
    }

    @Tool(
            name = "get_product",
            readOnly = true,
            description =
                    "Get the full details of one product by its id (the productKey returned by"
                            + " search_products / recommend_products): specs, selling points, description and"
                            + " price band. Use this to answer specific product questions accurately.")
    public String getProduct(
            RuntimeContext ctx,
            @ToolParam(name = "productId", description = "The productKey from search/recommend")
                    String productId) {
        if (!capabilities.contains("QUERY")) {
            return "Product lookup is not enabled for this agent.";
        }
        try {
            RankedProduct product = catalog.getByKeyOrId(agentId, productId).orElse(null);
            if (product == null) {
                return "Product not found or not visible to this agent: " + productId;
            }
            var sb = new StringBuilder();
            sb.append("name: ").append(product.name()).append('\n');
            sb.append("brand: ").append(nullToEmpty(product.brand())).append('\n');
            sb.append("category: ").append(nullToEmpty(product.category())).append('\n');
            sb.append("price: ").append(renderPrice(product)).append('\n');
            if (!product.scenarioTags().isEmpty()) {
                sb.append("scenario_tags: ").append(String.join(", ", product.scenarioTags())).append('\n');
            }
            if (product.specJson() != null && !product.specJson().isBlank() && !"{}".equals(product.specJson())) {
                sb.append("specs: ").append(product.specJson()).append('\n');
            }
            if (product.sellingPoints() != null && !product.sellingPoints().isBlank()) {
                sb.append("selling_points:\n").append(product.sellingPoints().trim()).append('\n');
            }
            if (product.description() != null && !product.description().isBlank()) {
                sb.append("description:\n").append(product.description().trim()).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("get_product failed for agent {}: {}", agentId, e.getMessage(), e);
            return "Error getting product: " + e.getMessage();
        }
    }

    @Tool(
            name = "list_solutions",
            readOnly = true,
            description =
                    "List the sales solutions/packages available to this agent. Each solution bundles"
                            + " multiple products for a customer scenario. Requires the SOLUTION capability"
                            + " (typically sales agents).")
    public String listSolutions(RuntimeContext ctx) {
        if (!capabilities.contains("SOLUTION")) {
            return "Solution lookup is not enabled for this agent.";
        }
        try {
            List<RankedSolution> solutions = solutionCatalog.listActive();
            if (solutions.isEmpty()) {
                return "No solutions are available.";
            }
            var sb = new StringBuilder();
            for (RankedSolution s : solutions) {
                sb.append("- id: ")
                        .append(s.solutionKey())
                        .append("\n  name: ")
                        .append(s.name())
                        .append("\n  target: ")
                        .append(nullToEmpty(s.targetCustomer()))
                        .append("\n  scenario: ")
                        .append(nullToEmpty(s.scenario()))
                        .append('\n');
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("list_solutions failed for agent {}: {}", agentId, e.getMessage(), e);
            return "Error listing solutions: " + e.getMessage();
        }
    }

    @Tool(
            name = "get_solution",
            readOnly = true,
            description =
                    "Get full details of one solution by its id (solutionKey), including its bundled"
                            + " products (with quantities and roles). Use this to propose a package.")
    public String getSolution(
            RuntimeContext ctx,
            @ToolParam(name = "solutionId", description = "The solutionKey from list_solutions")
                    String solutionId) {
        if (!capabilities.contains("SOLUTION")) {
            return "Solution lookup is not enabled for this agent.";
        }
        try {
            RankedSolution s = solutionCatalog.getByKey(solutionId).orElse(null);
            if (s == null) {
                return "Solution not found: " + solutionId;
            }
            var sb = new StringBuilder();
            sb.append("name: ").append(s.name()).append('\n');
            sb.append("target_customer: ").append(nullToEmpty(s.targetCustomer())).append('\n');
            sb.append("scenario: ").append(nullToEmpty(s.scenario())).append('\n');
            if (s.priceNote() != null && !s.priceNote().isBlank()) {
                sb.append("price_note: ").append(s.priceNote()).append('\n');
            }
            if (s.description() != null && !s.description().isBlank()) {
                sb.append("description:\n").append(s.description().trim()).append('\n');
            }
            sb.append("items:\n");
            for (SolutionItemView item : s.items()) {
                sb.append("  - ")
                        .append(item.role())
                        .append(" x")
                        .append(item.quantity())
                        .append(' ')
                        .append(item.productName())
                        .append(" (")
                        .append(item.productKey())
                        .append(")\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("get_solution failed for agent {}: {}", agentId, e.getMessage(), e);
            return "Error getting solution: " + e.getMessage();
        }
    }

    private String renderProducts(List<RankedProduct> products) {
        if (products.isEmpty()) {
            return "No products match the search criteria.";
        }
        var sb = new StringBuilder();
        for (RankedProduct p : products) {
            sb.append("- id: ")
                    .append(p.productKey())
                    .append("\n  name: ")
                    .append(p.name())
                    .append("\n  brand: ")
                    .append(nullToEmpty(p.brand()))
                    .append(" | category: ")
                    .append(nullToEmpty(p.category()))
                    .append(" | price: ")
                    .append(renderPrice(p));
            if (!p.scenarioTags().isEmpty()) {
                sb.append("\n  tags: ").append(String.join(", ", p.scenarioTags()));
            }
            if (p.sellingPoints() != null && !p.sellingPoints().isBlank()) {
                sb.append("\n  卖点: ").append(firstLine(p.sellingPoints()));
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private String renderPrice(RankedProduct p) {
        if (p.priceMin() == null && p.priceMax() == null) return "询价";
        String cur = p.currency() == null ? "CNY" : p.currency();
        if (p.priceMin() != null && p.priceMax() != null && p.priceMin().compareTo(p.priceMax()) != 0) {
            return cur + " " + p.priceMin().stripTrailingZeros().toPlainString()
                    + "-" + p.priceMax().stripTrailingZeros().toPlainString();
        }
        BigDecimal single = p.priceMin() != null ? p.priceMin() : p.priceMax();
        return cur + " " + single.stripTrailingZeros().toPlainString();
    }

    private String firstLine(String s) {
        int nl = s.indexOf('\n');
        return nl < 0 ? s.trim() : s.substring(0, nl).trim();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

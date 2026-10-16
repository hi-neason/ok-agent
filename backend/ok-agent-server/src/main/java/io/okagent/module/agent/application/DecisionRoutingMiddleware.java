package io.okagent.module.agent.application;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.okagent.module.decision.application.DecisionRoutingService;
import java.util.ArrayList;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Applies configured decision routing to every Harness entry point, including IM and debug. */
public final class DecisionRoutingMiddleware implements MiddlewareBase {
    public static final String CTX_ALREADY_CLASSIFIED = "okagent.routing.classified";
    private static final Logger log = LoggerFactory.getLogger(DecisionRoutingMiddleware.class);
    private final ResolvedAgentConfig config;
    private final DecisionRoutingService decisions;

    public DecisionRoutingMiddleware(ResolvedAgentConfig config, DecisionRoutingService decisions) {
        this.config = config;
        this.decisions = decisions;
    }

    static boolean enabled(ResolvedAgentConfig config, boolean asLeaf) {
        return !asLeaf
                && (config.getDecisionModelAssetId() != null || config.getResolvedDecisionModelAsset() != null)
                && config.getSubagents() != null
                && !config.getSubagents().isEmpty();
    }

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent, RuntimeContext ctx, AgentInput input, Function<AgentInput, Flux<AgentEvent>> next) {
        return Flux.defer(() -> {
            if (Boolean.TRUE.equals(ctx.get(CTX_ALREADY_CLASSIFIED)) || !enabled(config, false)) {
                return next.apply(input);
            }
            String query = "";
            for (Msg message : input.msgs()) {
                if (message.getRole() == MsgRole.USER) query = message.getTextContent();
            }
            if (query == null || query.isBlank() || config.getResolvedIntents().isEmpty()) return next.apply(input);
            String finalQuery = query;
            long started = System.nanoTime();
            return Mono.fromCallable(() -> DecisionRoutingPolicy.classify(config, decisions.choose(config, finalQuery)))
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnError(error -> {
                        var failure =
                                error instanceof io.okagent.module.decision.application.DecisionModelException provider
                                        ? provider
                                        : null;
                        log.warn(
                                "Decision routing failed agent={} durationMs={} causeType={} kind={} status={}",
                                config.getId(),
                                (System.nanoTime() - started) / 1_000_000,
                                error.getClass().getSimpleName(),
                                failure == null ? "CONFIGURATION" : failure.kind(),
                                failure == null ? 0 : failure.statusCode());
                    })
                    .flatMapMany(classification -> {
                        log.info(
                                "Decision routing completed agent={} durationMs={} matched={} delegated={} confidence={}",
                                config.getId(),
                                (System.nanoTime() - started) / 1_000_000,
                                classification.intentKey() != null,
                                classification.targetSubagentKey() != null,
                                classification.confidence());
                        String instruction = DecisionRoutingPolicy.instruction(classification);
                        if (instruction == null) return next.apply(input);
                        var messages = new ArrayList<Msg>(input.msgs());
                        messages.add(
                                0,
                                Msg.builder()
                                        .role(MsgRole.USER)
                                        .name("routing")
                                        .textContent(instruction)
                                        .build());
                        return next.apply(new AgentInput(messages));
                    });
        });
    }
}

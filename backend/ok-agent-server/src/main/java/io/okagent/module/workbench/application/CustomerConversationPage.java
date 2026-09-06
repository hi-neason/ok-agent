package io.okagent.module.workbench.application;
import java.util.List;
/** One database page of customers, retaining their matching sessions for channel grouping. */
public record CustomerConversationPage(List<DialogueWorkItemView> sessions, long totalCustomers, int totalPages) {}

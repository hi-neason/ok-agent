/**
 * Agent management module for editable configuration and the version/release lifecycle.
 *
 * <p>This module may produce immutable snapshots for the runtime, but runtime execution must not
 * read editable drafts through this module.
 */
package io.okagent.module.agentmanager;

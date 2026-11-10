package com.performance.platform.plugin;

/**
 * Opt-in for resources that must also be released on execution completion.
 * Other restart cleaners may retain durable outputs and are not called at completion.
 */
@FunctionalInterface
public interface ExecutionCompletionCleaner extends StatefulResourceCleaner {
}

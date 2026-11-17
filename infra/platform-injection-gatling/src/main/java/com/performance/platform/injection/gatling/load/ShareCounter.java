package com.performance.platform.injection.gatling.load;

import com.performance.platform.domain.execution.GrantShare;
import com.performance.platform.domain.injection.WorkloadPlan;
import com.performance.platform.domain.injection.WorkloadPlanCodec;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Compteur {@code {index}} partitionne par part (ADR-067) : valeurs 1-based disjointes entre agents
 * ({@code index + n x count + 1}). Avec {@code GrantShare.single()} : 1, 2, 3...
 * Porte aussi la derivation de graine et le transport de la part vers le worker (system properties).
 */
public final class ShareCounter {

    public static final String PROP_SHARE_INDEX = "platform.share.index";
    public static final String PROP_SHARE_COUNT = "platform.share.count";
    public static final String PROP_SEED = "platform.seed";

    private final int index;
    private final int count;
    private final AtomicLong local = new AtomicLong();

    public ShareCounter(GrantShare share) {
        Objects.requireNonNull(share, "share must not be null");
        this.index = share.index();
        this.count = share.count();
    }

    /** Lit la part depuis les system properties du worker ; absente -> part unique. */
    public static ShareCounter fromSystemProperties() {
        return new ShareCounter(shareFromSystemProperties());
    }

    public static GrantShare shareFromSystemProperties() {
        String i = System.getProperty(PROP_SHARE_INDEX);
        String c = System.getProperty(PROP_SHARE_COUNT);
        if (i == null || c == null) return GrantShare.single();
        return GrantShare.equal(Integer.parseInt(i), Integer.parseInt(c));
    }

    public long next() {
        return local.getAndIncrement() * count + index + 1;
    }

    /** Graine = 64 premiers bits du hash du plan XOR index de la part. */
    public static long seed(WorkloadPlan plan, GrantShare share) {
        String hex = WorkloadPlanCodec.hash(plan);
        return Long.parseUnsignedLong(hex.substring(0, 16), 16) ^ share.index();
    }

    /** Proprietes transmises au worker via le run directory (spec.json). */
    public static Map<String, String> toSystemProperties(WorkloadPlan plan, GrantShare share) {
        return Map.of(PROP_SHARE_INDEX, String.valueOf(share.index()),
                PROP_SHARE_COUNT, String.valueOf(share.count()),
                PROP_SEED, Long.toUnsignedString(seed(plan, share)));
    }
}

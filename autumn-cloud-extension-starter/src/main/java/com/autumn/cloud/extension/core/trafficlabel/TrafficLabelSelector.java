package com.autumn.cloud.extension.core.trafficlabel;

import java.util.List;
import java.util.function.Function;

/**
 * 流量标签选择器：从候选实例中筛选与请求流量标签匹配的实例。
 *
 * <p>规则：</p>
 * <ul>
 *     <li>实例未打标（metadata 无 trafficLabel 或空白）视为 {@code default} 流量标签；</li>
 *     <li>请求未指定流量标签视为 {@code default} 流量标签；</li>
 *     <li>命中流量标签无实例且允许回落时：default 流量标签实例 → 全部实例，逐级兜底；</li>
 *     <li>不允许回落时，无匹配即返回空列表（快速失败）。</li>
 * </ul>
 */
public final class TrafficLabelSelector {

    private TrafficLabelSelector() {
    }

    public static String normalize(String trafficLabel) {
        return (trafficLabel == null || trafficLabel.isBlank()) ? TrafficLabel.DEFAULT_LABEL : trafficLabel.trim();
    }

    /**
     * 按请求流量标签筛选实例。
     *
     * @param candidates     候选实例
     * @param requestedLane  请求流量标签，null/空白视为 default
     * @param laneExtractor  从实例中提取流量标签的函数
     * @param fallbackToDefault 目标流量标签无实例时是否回落到 default 流量标签
     * @return 匹配的实例列表
     */
    public static <T> List<T> select(List<T> candidates, String requestedLane,
                                     Function<T, String> laneExtractor, boolean fallbackToDefault) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        String request = normalize(requestedLane);
        List<T> direct = filter(candidates, request, laneExtractor);
        if (!direct.isEmpty()) {
            return direct;
        }
        if (request.equals(TrafficLabel.DEFAULT_LABEL)) {
            // 请求 default 流量标签但无打标实例：允许回落则放行全部
            return fallbackToDefault ? List.copyOf(candidates) : List.of();
        }
        if (!fallbackToDefault) {
            return List.of();
        }
        List<T> defaults = filter(candidates, TrafficLabel.DEFAULT_LABEL, laneExtractor);
        return defaults.isEmpty() ? List.copyOf(candidates) : defaults;
    }

    private static <T> List<T> filter(List<T> candidates, String trafficLabel, Function<T, String> laneExtractor) {
        return candidates.stream()
                .filter(c -> normalize(laneExtractor.apply(c)).equals(trafficLabel))
                .toList();
    }
}

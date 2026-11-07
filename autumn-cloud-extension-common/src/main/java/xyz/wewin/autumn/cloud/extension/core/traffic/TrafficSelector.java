package xyz.wewin.autumn.cloud.extension.core.traffic;

import java.util.List;
import java.util.function.Function;

/**
 * 流量选择器：从候选实例中筛选与请求流量匹配的实例。
 *
 * <p>规则：</p>
 * <ul>
 *     <li>实例未打标（metadata 无 traffic 或空白）视为 {@code default} 主干流量；</li>
 *     <li>请求未指定流量视为 {@code default} 主干流量；</li>
 *     <li>命中流量无实例且允许回落时：default 主干实例 → 全部实例，逐级兜底；</li>
 *     <li>不允许回落时，无匹配即返回空列表（快速失败）。</li>
 * </ul>
 */
public final class TrafficSelector {

    private TrafficSelector() {
    }

    public static String normalize(String traffic) {
        return (traffic == null || traffic.isBlank()) ? Traffic.DEFAULT : traffic.trim();
    }

    /**
     * 按请求流量筛选实例。
     *
     * @param candidates     候选实例
     * @param requested      请求流量，null/空白视为 default
     * @param extractor      从实例中提取流量的函数
     * @param fallbackToMain 目标流量无实例时是否回落到 default 主干（字段名/配置项保持兼容）
     * @return 匹配的实例列表
     */
    public static <T> List<T> select(List<T> candidates, String requested,
                                     Function<T, String> extractor, boolean fallbackToMain) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        String request = normalize(requested);
        List<T> direct = filter(candidates, request, extractor);
        if (!direct.isEmpty()) {
            return direct;
        }
        if (request.equals(Traffic.DEFAULT)) {
            // 请求 default 主干但无打标实例：允许回落则放行全部
            return fallbackToMain ? List.copyOf(candidates) : List.of();
        }
        if (!fallbackToMain) {
            return List.of();
        }
        List<T> mains = filter(candidates, Traffic.DEFAULT, extractor);
        return mains.isEmpty() ? List.copyOf(candidates) : mains;
    }

    private static <T> List<T> filter(List<T> candidates, String traffic, Function<T, String> extractor) {
        return candidates.stream()
                .filter(c -> normalize(extractor.apply(c)).equals(traffic))
                .toList();
    }
}

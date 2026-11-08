package xyz.wewin.autumn.cloud.extension.propagation;

/**
 * header 透传的筛选模式。
 *
 * <p>默认 {@link #WHITELIST}：只有 {@link HeaderPropagationProperties#include()}
 * 中列出的 header 才会被带到下游，天然避免"整包透传导致 header 越滚越肥"。</p>
 */
public enum PropagationMode {

    /** 白名单：只透传显式列出的 header。 */
    WHITELIST,

    /**
     * 黑名单：除 {@link HeaderPropagationProperties#exclude()}、传输层 header、
     * 链路追踪 header 之外全部透传。
     *
     * <p>注意：该模式会把业务侧未预期的 header 一并带下去（例如内部签名、
     * 灰度标记、内部路由信息），只建议在内部可信调用链上使用。</p>
     */
    BLACKLIST
}

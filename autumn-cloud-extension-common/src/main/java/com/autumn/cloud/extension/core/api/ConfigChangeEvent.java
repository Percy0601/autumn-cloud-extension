package com.autumn.cloud.extension.core.api;

import java.util.Map;

/**
 * 配置变更事件。
 *
 * @param dataId      配置 ID
 * @param group       分组
 * @param namespace   命名空间
 * @param oldContent  旧配置内容（首次监听时为 null）
 * @param newContent  新配置内容
 * @param changeType  变更类型（MODIFY/ADD/DELETE）
 */
public record ConfigChangeEvent(
        String dataId,
        String group,
        String namespace,
        String oldContent,
        String newContent,
        ChangeType changeType
) {

    /**
     * 配置变更类型
     */
    public enum ChangeType {
        /** 配置新增 */
        ADD,
        /** 配置修改 */
        MODIFY,
        /** 配置删除 */
        DELETE
    }

    /**
     * 判断是否为新增事件
     */
    public boolean isAdd() {
        return changeType == ChangeType.ADD;
    }

    /**
     * 判断是否为修改事件
     */
    public boolean isModify() {
        return changeType == ChangeType.MODIFY;
    }

    /**
     * 判断是否为删除事件
     */
    public boolean isDelete() {
        return changeType == ChangeType.DELETE;
    }
}

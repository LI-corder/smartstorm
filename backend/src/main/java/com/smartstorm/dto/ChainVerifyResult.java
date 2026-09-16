package com.smartstorm.dto;

import lombok.Data;

/**
 * 哈希链完整性校验结果。
 *
 * <p>{@code ok=true} 时 {@code brokenSeq} 与 {@code reason} 均为 null；
 * 否则定位到首个出问题的 seq，供前端高亮。</p>
 */
@Data
public class ChainVerifyResult {

    /** 整条链是否完整 */
    private boolean ok;

    /** 参与校验的操作总数 */
    private int totalOps;

    /** 首个出问题的 seq；链完整时为 null */
    private Long brokenSeq;

    /** 出问题的原因（中文，可直接展示给用户）；链完整时为 null */
    private String reason;

    /** 校验通过 */
    public ChainVerifyResult pass() {
        this.ok = true;
        this.brokenSeq = null;
        this.reason = null;
        return this;
    }

    /** 校验失败，定位到首个出问题的 seq */
    public ChainVerifyResult broken(Long brokenSeq, String reason) {
        this.ok = false;
        this.brokenSeq = brokenSeq;
        this.reason = reason;
        return this;
    }
}

package com.smartstorm.common;

/**
 * 未授权异常：登录失败 / 凭证无效。由全局异常处理器映射为 HTTP 401。
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}

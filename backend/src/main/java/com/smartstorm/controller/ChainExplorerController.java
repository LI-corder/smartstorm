package com.smartstorm.controller;

import com.smartstorm.common.Result;
import com.smartstorm.dto.ChainBlockVO;
import com.smartstorm.dto.ChainOverviewVO;
import com.smartstorm.service.ChainExplorerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 区块信息接口（区块链浏览器）。
 *
 * <p>路径是<b>链级</b>的 {@code /api/chain/**}，不是房间级 —— 区块是全链共享的，
 * 不属于任何房间。房间与链的关联体现在区块内交易的存证标注上（房间号 + seq 区间）。</p>
 *
 * <p>三个都是读接口，匿名可访问（被 {@code SecurityConfig} 的
 * {@code anyRequest().permitAll()} 兜住），与房间存证的读接口一致。</p>
 *
 * <p>链未启用时统一返回 {@code chainEnabled=false} 与空数据，而不是报错 ——
 * 前端据此渲染说明性的降级态。</p>
 */
@RestController
@RequestMapping("/api/chain")
public class ChainExplorerController {

    private final ChainExplorerService chainExplorerService;

    public ChainExplorerController(ChainExplorerService chainExplorerService) {
        this.chainExplorerService = chainExplorerService;
    }

    /** 链概览：区块高度、链上交易数、共识节点数，以及本系统的存证批次统计 */
    @GetMapping("/overview")
    public Result<ChainOverviewVO> overview() {
        return Result.ok(chainExplorerService.overview());
    }

    /** 最新区块列表（高度从高到低），含本系统存证交易的标注 */
    @GetMapping("/blocks")
    public Result<List<ChainBlockVO>> blocks(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.ok(chainExplorerService.blocks(limit));
    }

    /** 指定高度的区块详情 */
    @GetMapping("/blocks/{number}")
    public Result<ChainBlockVO> block(@PathVariable long number) {
        ChainBlockVO vo = chainExplorerService.block(number);
        if (vo == null) {
            return Result.fail(404, "区块不存在，或存证链未启用");
        }
        return Result.ok(vo);
    }
}

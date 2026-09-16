package com.smartstorm.controller;

import com.smartstorm.common.Result;
import com.smartstorm.dto.AnchorVO;
import com.smartstorm.dto.ChainStatusVO;
import com.smartstorm.dto.ChainVerifyResult;
import com.smartstorm.dto.MerkleProofVO;
import com.smartstorm.service.AnchorService;
import com.smartstorm.service.HashChainService;
import com.smartstorm.service.RoomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 操作日志存证接口。
 *
 * <p>路径沿用项目惯例 {@code /api/rooms/{roomId}/...}（注意 rooms 是复数）。</p>
 *
 * <p>读接口匿名可访问，与 {@code /api/rooms/{id}/analysis/latest} 一致，供刷新与晚加入
 * 恢复；只有手动触发锚定需要登录（规则见 {@code SecurityConfig}）。</p>
 */
@RestController
@RequestMapping("/api/rooms/{roomId}/chain")
public class ChainController {

    private final AnchorService anchorService;
    private final HashChainService hashChainService;
    private final RoomService roomService;

    public ChainController(AnchorService anchorService,
                           HashChainService hashChainService,
                           RoomService roomService) {
        this.anchorService = anchorService;
        this.hashChainService = hashChainService;
        this.roomService = roomService;
    }

    /** 房间存证状态摘要（总操作数、已锚定到哪、锚点数、链是否启用） */
    @GetMapping("/status")
    public Result<ChainStatusVO> status(@PathVariable Long roomId) {
        if (!roomService.exists(roomId)) {
            return Result.fail(404, "房间不存在");
        }
        return Result.ok(anchorService.status(roomId));
    }

    /**
     * 重算整条哈希链，校验完整性。
     *
     * <p>这个接口<b>不依赖区块链</b> —— 链没配好、VM 没开，照样能验本地哈希链。</p>
     */
    @GetMapping("/verify")
    public Result<ChainVerifyResult> verify(@PathVariable Long roomId) {
        if (!roomService.exists(roomId)) {
            return Result.fail(404, "房间不存在");
        }
        return Result.ok(hashChainService.verify(roomId));
    }

    /** 锚点列表（最近的在前） */
    @GetMapping("/anchors")
    public Result<List<AnchorVO>> anchors(@PathVariable Long roomId) {
        if (!roomService.exists(roomId)) {
            return Result.fail(404, "房间不存在");
        }
        return Result.ok(anchorService.listAnchors(roomId));
    }

    /** 某条操作的 Merkle 存在性证明（可用来独立验证它属于某个已上链的批次） */
    @GetMapping("/proof")
    public Result<MerkleProofVO> proof(@PathVariable Long roomId, @RequestParam("seq") long seq) {
        if (!roomService.exists(roomId)) {
            return Result.fail(404, "房间不存在");
        }
        MerkleProofVO vo = anchorService.proof(roomId, seq);
        if (vo == null) {
            return Result.fail(404, "该操作尚未被任何存证批次覆盖");
        }
        return Result.ok(vo);
    }

    /** 手动触发一次锚定，跳过阈值判断（需登录）。答辩演示时不必等定时任务。 */
    @PostMapping("/anchor")
    public Result<AnchorVO> anchorNow(@PathVariable Long roomId) {
        if (!roomService.exists(roomId)) {
            return Result.fail(404, "房间不存在");
        }
        AnchorVO vo = anchorService.anchorNow(roomId);
        if (vo == null) {
            return Result.fail(400, "没有可锚定的新操作");
        }
        return Result.ok(vo);
    }
}

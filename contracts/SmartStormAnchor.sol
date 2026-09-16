// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.11;

/**
 * @title SmartStormAnchor
 * @notice SmartStorm 操作日志的链上存证合约。
 *
 * 合约刻意做得很薄：只存 32 字节的 Merkle 根，不做任何业务逻辑。
 * 一批操作在链下聚成一棵 Merkle 树，只有根上链 —— 这样一条操作对应
 * 一次交易的成本 O(n) 降到 O(1)，单条操作靠 Merkle proof 照样可证。
 *
 * 只上哈希，便利贴明文绝不上链：链上数据不可删除，而会议内容可能是商业机密。
 *
 * 部署方式：在 FISCO BCOS 控制台里 deploy SmartStormAnchor
 * （需先把本文件放到 console/contracts/solidity/ 下）。
 */
contract SmartStormAnchor {

    struct Anchor {
        bytes32 root;        // 本批操作的 Merkle 根
        uint256 fromSeq;     // 本批起始 seq
        uint256 toSeq;       // 本批结束 seq
        uint256 timestamp;   // 上链时间（区块时间戳）
        address submitter;   // 提交者地址
    }

    /// 房间 id => 该房间的锚点序列（只追加）
    mapping(uint256 => Anchor[]) private _anchors;

    event Anchored(
        uint256 indexed roomId,
        bytes32 root,
        uint256 fromSeq,
        uint256 toSeq,
        uint256 index
    );

    /**
     * @notice 锚定一批操作的 Merkle 根
     * @param roomId  房间 id
     * @param root    本批操作的 Merkle 根
     * @param fromSeq 本批起始 seq
     * @param toSeq   本批结束 seq
     * @return 该锚点在房间序列中的下标
     */
    function anchor(uint256 roomId, bytes32 root, uint256 fromSeq, uint256 toSeq)
        public
        returns (uint256)
    {
        _anchors[roomId].push(Anchor(root, fromSeq, toSeq, block.timestamp, msg.sender));
        uint256 idx = _anchors[roomId].length - 1;
        emit Anchored(roomId, root, fromSeq, toSeq, idx);
        return idx;
    }

    /**
     * @notice 读取某个锚点。view 方法，验证不花 gas。
     * @return root, fromSeq, toSeq, timestamp, submitter
     */
    function getAnchor(uint256 roomId, uint256 index)
        public
        view
        returns (bytes32, uint256, uint256, uint256, address)
    {
        Anchor storage a = _anchors[roomId][index];
        return (a.root, a.fromSeq, a.toSeq, a.timestamp, a.submitter);
    }

    /// @notice 某房间已锚定的批次数
    function count(uint256 roomId) public view returns (uint256) {
        return _anchors[roomId].length;
    }
}

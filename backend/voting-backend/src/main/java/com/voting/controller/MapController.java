package com.voting.controller;

import com.voting.common.BizException;
import com.voting.common.Result;
import com.voting.service.MapService;
import com.voting.util.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/map")
public class MapController {

    private final MapService mapService;

    public MapController(MapService mapService) {
        this.mapService = mapService;
    }

    /** 获奖地图: 部门×主题交叉表 */
    @GetMapping("/winner")
    public Result<Map<String, Object>> winner() {
        requireAdmin();
        return Result.ok(mapService.winnerMap());
    }

    private void requireAdmin() {
        String role = UserContext.getRole();
        if (!"admin".equals(role) && !"super_admin".equals(role)) {
            throw new BizException(403, "无权限执行该操作");
        }
    }
}

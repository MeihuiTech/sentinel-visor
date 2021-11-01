package com.mei.hui.browser.Controller;

import com.mei.hui.browser.model.BlockPageListVO;
import com.mei.hui.browser.model.PowerRankingVO;
import com.mei.hui.browser.model.RankingBO;
import com.mei.hui.browser.service.BlockService;
import com.mei.hui.util.BasePage;
import com.mei.hui.util.PageResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Api(tags = "首页排行榜-出块份数")
@RestController
@RequestMapping("/block")
public class BlockController {
    @Autowired
    private BlockService blockService;

    @ApiOperation(value = "出块份数排行榜")
    @PostMapping("/transferRecordDetail")
    public PageResult<BlockPageListVO> blockPageList(@RequestBody RankingBO rankingBO) throws IOException {
        return blockService.blockPageList(rankingBO);
    }

}

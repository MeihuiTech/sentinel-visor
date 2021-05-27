package com.mei.hui.miner.SystemController;

import com.mei.hui.config.HttpRequestUtil;
import com.mei.hui.miner.common.MinerError;
import com.mei.hui.miner.common.enums.CurrencyEnum;
import com.mei.hui.miner.entity.*;
import com.mei.hui.miner.feign.vo.AggMinerVO;
import com.mei.hui.miner.model.SysMinerInfoBO;
import com.mei.hui.miner.model.XchMinerDetailBO;
import com.mei.hui.miner.service.ISysAggAccountDailyService;
import com.mei.hui.miner.service.ISysAggPowerDailyService;
import com.mei.hui.miner.service.ISysMinerInfoService;
import com.mei.hui.util.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 矿工信息Controller
 *
 * @author ruoyi
 * @date 2021-03-02
 */
@Api(tags = "矿工信息")
@RestController
@RequestMapping("/system/miner")
public class SysMinerInfoController<ISysMachineInfoService> {

    @Autowired
    private ISysMinerInfoService sysMinerInfoService;
    @Autowired
    private ISysAggPowerDailyService sysAggPowerDailyService;
    @Autowired
    private ISysAggAccountDailyService sysAggAccountDailyService;

    @ApiOperation(value = "账户按天聚合信息")
    @GetMapping(value = "/{id}/dailyAccount")
    public PageResult dailyAccount(@PathVariable("id") Long id) {
        Long currencyId = HttpRequestUtil.getCurrencyId();
        if(CurrencyEnum.FIL.getCurrencyId() == currencyId){//fil 币
            return sysMinerInfoService.dailyAccount(id);
        }else if(CurrencyEnum.CHIA.getCurrencyId() == currencyId){//起亚币
            return sysMinerInfoService.chiaDailyAccount(id);
        }
        return null;
    }

    /**
     * 查询矿工信息列表
     */
    @ApiOperation(value = "矿工列表不分页")
    @GetMapping("/listAll")
    public PageResult<SysMinerInfo> listAll(SysMinerInfo sysMinerInfo){
        Long currencyId = HttpRequestUtil.getCurrencyId();
        List<SysMinerInfo> list = null;
        if(CurrencyEnum.FIL.getCurrencyId() == currencyId){
            list = sysMinerInfoService.selectSysMinerInfoList(sysMinerInfo);
        }else if(CurrencyEnum.CHIA.getCurrencyId() == currencyId){
            list = sysMinerInfoService.findXchMinerList();
        }
        return new PageResult(list.size(),list);
    }


    /**
     * 获取矿工信息详细信息
     */
    @ApiOperation(value = "矿工详情")
    @GetMapping(value = "/{id}")
    public Result getInfo(@PathVariable("id") Long id){
        Long currencyId = HttpRequestUtil.getCurrencyId();
        if(CurrencyEnum.FIL.getCurrencyId() == currencyId){//fil 币
            SysMinerInfo miner = sysMinerInfoService.selectSysMinerInfoById(id);
            if (miner == null) {
                throw  MyException.fail(MinerError.MYB_222222.getCode(),"资源不存在");
            }
            return Result.success(miner);
        }else if(CurrencyEnum.CHIA.getCurrencyId() == currencyId){//起亚币
            XchMinerDetailBO xchMinerDetailBO = sysMinerInfoService.getXchMinerById(id);
            if (xchMinerDetailBO == null) {
                throw  MyException.fail(MinerError.MYB_222222.getCode(),"资源不存在");
            }
            return Result.success(xchMinerDetailBO);
        }
        return Result.fail(MinerError.MYB_222222.getCode(),"用户当前币种异常");
    }

    @ApiOperation(value = "算力按天聚合信息")
    @GetMapping(value = "/{id}/dailyPower")
    public Map<String,Object> dailyPower(@PathVariable("id") Long id) {
        Long currencyId = HttpRequestUtil.getCurrencyId();
        if(CurrencyEnum.FIL.getCurrencyId() == currencyId){//fil 币
            return sysMinerInfoService.dailyPower(id);
        }else if(CurrencyEnum.CHIA.getCurrencyId() == currencyId){//起亚币
            return sysMinerInfoService.chiaDailyPower(id);
        }
        Map<String,Object> map = new HashMap<>();
        map.put("code",MinerError.MYB_222222.getCode());
        map.put("msg","获取聚合信息错误");
        return map;
    }

    /**
     * 查询矿工信息列表
     */
    @ApiOperation(value = "矿工列表")
    @GetMapping("/list")
    public Map<String,Object> list(SysMinerInfoBO sysMinerInfoBO)
    {
        return sysMinerInfoService.findPage(sysMinerInfoBO);
    }

    /**
     * 获取当前矿工的矿机列表
     */
    @ApiOperation(value = "矿机列表")
    @GetMapping(value = "machines/{id}")
    public Map<String,Object> machines(@PathVariable("id") Long id,int pageNum,int pageSize) {
        return sysMinerInfoService.machines(id,pageNum,pageSize);
    }

    /**
     * 通过userid 集合批量获取旷工
     */
    @ApiOperation(value = "通过userid 集合批量获取旷工")
    @GetMapping(value = "/findBatchMinerByUserId")
    public Result<List<AggMinerVO>> findBatchMinerByUserId(@RequestParam("userIds") List<Long> userIds) {
        return sysMinerInfoService.findBatchMinerByUserId(userIds);
    }

}

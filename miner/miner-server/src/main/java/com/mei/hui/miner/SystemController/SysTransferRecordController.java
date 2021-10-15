package com.mei.hui.miner.SystemController;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mei.hui.config.HttpRequestUtil;
import com.mei.hui.miner.common.MinerError;
import com.mei.hui.miner.entity.Currency;
import com.mei.hui.miner.entity.FilAdminUser;
import com.mei.hui.miner.entity.SysTransferRecord;
import com.mei.hui.miner.feign.vo.TakeOutInfoBO;
import com.mei.hui.miner.feign.vo.TakeOutInfoVO;
import com.mei.hui.miner.model.*;
import com.mei.hui.miner.service.FilAdminUserService;
import com.mei.hui.miner.service.ISysCurrencyService;
import com.mei.hui.miner.service.ISysTransferRecordService;
import com.mei.hui.util.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统划转记录Controller
 * 
 * @author ruoyi
 * @date 2021-03-08
 */
@Slf4j
@Api(tags = "系统划转记录")
@RestController
@RequestMapping("/system/transfer")
public class SysTransferRecordController
{
    @Autowired
    private ISysTransferRecordService sysTransferRecordService;

    @Autowired
    private ISysCurrencyService sysCurrencyService;
    @Autowired
    private FilAdminUserService adminUserService;

    /**
     * 查询系统划转记录列表,普通用户
     */
    @ApiOperation(value = "查询系统划转记录列表,普通用户")
    @GetMapping("/list")
    public Map<String,Object> list(SysTransferRecord sysTransferRecord){
        return sysTransferRecordService.findTransferRecords(sysTransferRecord);
    }

    /**
     * 查询系统划转记录列表，管理员用户，管理员-矿池收益-用户提取记录分页
     */
    @ApiOperation(value = "查询系统划转记录列表，管理员用户",notes = "入参cloumName排序字段名称:\n" +
            "amount提取金额\n" +
            "fee平台收取手续费")
    @GetMapping("/listForAdmin")
    public Map<String,Object> listForAdmin(AggWithdrawBO aggWithdrawBO)
    {
        return sysTransferRecordService.selectSysTransferRecordListUserName(aggWithdrawBO);
    }

    /**
     * 获取系统划转记录详细信息
     */
    @ApiOperation(value = "获取系统划转记录详细信息-【废弃】")
    @GetMapping(value = "/{id}")
    public Result getInfo(@PathVariable("id") Long id)
    {
        return Result.success(sysTransferRecordService.selectSysTransferRecordById(id));
    }

    /**
     * 修改系统划转记录-管理员审核通过不通过
     */
    @PutMapping
    @ApiOperation(value = "修改系统划转记录")
    public Result edit(@RequestBody SysTransferRecord sysTransferRecord)
    {
        if(sysTransferRecord.getId() == null) {
            throw MyException.fail(MinerError.MYB_222222.getCode(),"id字段不能为空");
        }
        SysTransferRecord trans = sysTransferRecordService.selectSysTransferRecordById(sysTransferRecord.getId());
        if (trans == null) {
            throw MyException.fail(MinerError.MYB_222222.getCode(),"资源不存在");
        }
        if(StringUtils.isNotEmpty(sysTransferRecord.getToHash()) && sysTransferRecord.getToHash().length() > 128){
            throw MyException.fail(MinerError.MYB_222222.getCode(),"提取HASH长度过长");
        }
        if(StringUtils.isNotEmpty(sysTransferRecord.getFeeHash()) && sysTransferRecord.getFeeHash().length() > 128){
            throw MyException.fail(MinerError.MYB_222222.getCode(),"费用HASH长度过长");
        }
        if(StringUtils.isNotEmpty(sysTransferRecord.getRemark()) && sysTransferRecord.getRemark().length() > 2000){
            throw MyException.fail(MinerError.MYB_222222.getCode(),"备注信息长度过长");
        }
        int rows = sysTransferRecordService.updateSysTransferRecord(sysTransferRecord);

        return rows > 0 ? Result.OK : Result.fail(MinerError.MYB_222222.getCode(),"失败");
    }

    /**
     * 删除系统划转记录
     */
    @ApiOperation(value = "删除系统划转记录")
	@DeleteMapping("/{ids}")
    public Result remove(@PathVariable Long[] ids)
    {
        int rows = sysTransferRecordService.deleteSysTransferRecordByIds(ids);
        return rows > 0 ? Result.OK : Result.fail(MinerError.MYB_222222.getCode(),"失败");
    }

    /**
     * 查询用户收益
     */
    @ApiOperation(value = "查询用户收益",notes = "查询用户收益出参：\n" +
            "\n" +
            "totalEarning总收益\n" +
            "totalLockAward总锁仓收益\n" +
            "totalWithdraw用户总共已提取\n" +
            "availableEarning用户可提取金额\n" +
            "drawingEarning正在提币中的")
    @GetMapping("/getUserEarning")
    public Result getUserEarning(GetUserEarningInput input){
        Long currencyId = HttpRequestUtil.getCurrencyId();
        if(CurrencyEnum.FIL.getCurrencyId() == currencyId){
            return sysTransferRecordService.getUserEarning(input);
        }else if(CurrencyEnum.XCH.getCurrencyId() == currencyId){
            return sysTransferRecordService.getUserChiaEarning(input);
        }
        return null;
    }

    @ApiOperation(value = "管理员-矿池收益-根据币种分别显示“总手续费”和“今日手续费”",notes = "fee平台收取手续费\n" +
            "name货比种类,FIL,CHIA")
    @GetMapping("/getPoolEarning")
    public Result getPoolEarning() {
        //获取当前管理员负责管理的用户id 列表
        List<Long> userIds = adminUserService.findUserIdsByAdmin();
        if(userIds.size() == 0){
            return Result.OK;
        }
        List<TransferRecordFeeVO> allTransferRecordFeeVOList = sysTransferRecordService.selectTotalEarning(userIds);
        log.info("总手续费收益出参：【{}】",JSON.toJSON(allTransferRecordFeeVOList));
        Date todayBeginDate = DateUtils.getBeginOfDayDate();
        List<TransferRecordFeeVO> todayTransferRecordFeeVOList = sysTransferRecordService.selectTodayEarning(todayBeginDate,userIds);
        log.info("今日手续费收益出参：【{}】",JSON.toJSON(todayTransferRecordFeeVOList));
        List<TransferRecordFeeVO> resultAllTransferRecordFeeVOList = new ArrayList<>();
        List<TransferRecordFeeVO> resultTodayTransferRecordFeeVOList = new ArrayList<>();
        List<Currency> currencyList = sysCurrencyService.allCurrencyList();
        log.info("币种表列表：【{}】",JSON.toJSON(currencyList));

        // 1.先把币种表里的所有币种都生成数据，收益赋值0。       2.用真实数据替换步骤一的0
        for (Currency currency:currencyList){
            TransferRecordFeeVO resultAllTransferRecordFeeVO = new TransferRecordFeeVO();
            resultAllTransferRecordFeeVO.setName(currency.getName());
            resultAllTransferRecordFeeVO.setFee(BigDecimal.ZERO);
            if (allTransferRecordFeeVOList != null && allTransferRecordFeeVOList.size() > 0) {
                for (TransferRecordFeeVO transferRecordFeeVO:allTransferRecordFeeVOList){
                    if (currency.getName().equals(transferRecordFeeVO.getName())){
                        resultAllTransferRecordFeeVO.setFee(BigDecimalUtil.formatFour(transferRecordFeeVO.getFee()==null? BigDecimal.ZERO:transferRecordFeeVO.getFee()));
                        break;
                    }
                }
            }
            log.info("总手续费收益赋值：【{}】",JSON.toJSON(resultAllTransferRecordFeeVO));
            resultAllTransferRecordFeeVOList.add(resultAllTransferRecordFeeVO);

            TransferRecordFeeVO resultTodayTransferRecordFeeVO = new TransferRecordFeeVO();
            resultTodayTransferRecordFeeVO.setName(currency.getName());
            resultTodayTransferRecordFeeVO.setFee(BigDecimal.ZERO);
            if (todayTransferRecordFeeVOList != null && todayTransferRecordFeeVOList.size() > 0) {
                for (TransferRecordFeeVO transferRecordFeeVO:todayTransferRecordFeeVOList) {
                    if (currency.getName().equals(transferRecordFeeVO.getName())){
                        resultTodayTransferRecordFeeVO.setFee(BigDecimalUtil.formatFour(transferRecordFeeVO.getFee()==null? BigDecimal.valueOf(0):transferRecordFeeVO.getFee()));
                        break;
                    }
                }
            }
            log.info("今日手续费收益赋值：【{}】",JSON.toJSON(resultTodayTransferRecordFeeVO));
            resultTodayTransferRecordFeeVOList.add(resultTodayTransferRecordFeeVO);
        }

        PoolEarningVo poolEarningVo = new PoolEarningVo();
        poolEarningVo.setAllTransferRecordFeeVOList(resultAllTransferRecordFeeVOList);
        poolEarningVo.setTodayTransferRecordFeeVOList(resultTodayTransferRecordFeeVOList);
        return Result.success(poolEarningVo);
    }

    /**
     * 用户提币：
     * 1、先校验现有余额是否 大于 将要提取的fil, 余额 - 待提币中的fil > 即将提取的fil
     */
    @ApiOperation(value = "用户提币")
    @PostMapping("/withdraw")
    public Result withdraw(@Validated  @RequestBody SysTransferRecordWrap sysTransferRecordWrap)
    {
        if(sysTransferRecordWrap.getMinerId() == null){
            throw MyException.fail(MinerError.MYB_222222.getCode(),"请选择矿工");
        }
        return sysTransferRecordService.withdraw(sysTransferRecordWrap);
    }

    @ApiOperation(value = "获取用户提现金额、平台佣金")
    @PostMapping("/takeOutInfo")
    public Result<TakeOutInfoVO> takeOutInfo(@RequestBody TakeOutInfoBO takeOutInfoBO){
        return sysTransferRecordService.takeOutInfo(takeOutInfoBO);
    }

    @ApiOperation(value = "获取系统划转记录详细信息")
    @PostMapping("/transferRecordDetail")
    public Result<GetTransferRecordByIdVO> transferRecordDetail(@RequestBody GetTransferRecordByIdBO transferRecordByIdBO){
        return sysTransferRecordService.getTransferRecordById(transferRecordByIdBO);
    }
}

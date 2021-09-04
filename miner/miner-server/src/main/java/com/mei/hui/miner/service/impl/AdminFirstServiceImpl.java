package com.mei.hui.miner.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mei.hui.config.HttpRequestUtil;
import com.mei.hui.miner.entity.FilAdminUser;
import com.mei.hui.miner.mapper.ChiaMinerMapper;
import com.mei.hui.miner.mapper.SysMinerInfoMapper;
import com.mei.hui.miner.model.AdminFirstCollectVO;
import com.mei.hui.miner.model.PowerAvailableFilVO;
import com.mei.hui.miner.service.*;
import com.mei.hui.user.feign.feignClient.UserFeignClient;
import com.mei.hui.user.feign.vo.SysUserOut;
import com.mei.hui.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class AdminFirstServiceImpl implements IAdminFirstService {

    @Autowired
    private SysMinerInfoMapper sysMinerInfoMapper;

    @Autowired
    private ISysMinerInfoService sysMinerInfoService;

    @Autowired
    private IChiaMinerService chiaMinerService;

    @Autowired
    private ChiaMinerMapper chiaMinerMapper;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private ISysAggPowerDailyService sysAggPowerDailyService;
    @Autowired
    private FilAdminUserService adminUserService;
    @Autowired
    private ISysAggPowerHourService sysAggPowerHourService;

    /**
     * fil管理员首页-矿工统计数据
     * @return
     */
    @Override
    public AdminFirstCollectVO filAdminFirstAllCount() {
        AdminFirstCollectVO adminFirstCollectVO = new AdminFirstCollectVO();

        //获取当前管理员负责管理的用户id 列表
        List<Long> userIds = adminUserService.findUserIdsByAdmin();
        if(userIds.size() == 0){
            log.info("此管理员没有配置管理的矿工用户");
            return adminFirstCollectVO;
        }

        // 管理员首页-矿工统计数据-平台总资产，用的字段：挖矿账户余额, 单位FIL
        BigDecimal allBalanceMinerAccount = sysMinerInfoService.selectFilAllBalanceMinerAccount(userIds);
        adminFirstCollectVO.setAllBalanceMinerAccount(BigDecimalUtil.formatFour(allBalanceMinerAccount));
        // 管理员首页-矿工统计数据-平台有效算力
        BigDecimal allPowerAvailable = sysMinerInfoService.selectFilAllPowerAvailable(userIds);
        adminFirstCollectVO.setAllPowerAvailable(allPowerAvailable);
        // 管理员首页-矿工统计数据-活跃矿工
        Long allMinerCount = sysMinerInfoService.selectFilAllMinerIdCount(userIds);
        adminFirstCollectVO.setAllMinerCount(allMinerCount);

        // 管理员首页-矿工统计数据-当天出块份数
        // 查询FIL币算力按小时聚合表里近24小时所有的每小时出块份数总和
        String startDate = DateUtils.lDTYesterdayBeforeLocalDateTimeHour();
        String endDate = DateUtils.lDTBeforeBeforeLocalDateTimeHour();
        log.info("入参minerId：【{}】,startDate：【{}】，endDate：【{}】",null,startDate,endDate);
        Long twentyFourTotalBlocks = sysAggPowerHourService.selectTwentyFourTotalBlocks(CurrencyEnum.FIL.name(),null,startDate,endDate);
        log.info("查询FIL币算力按小时聚合表里近24小时所有的每小时出块份数总和出参：【{}】",twentyFourTotalBlocks);
        twentyFourTotalBlocks = twentyFourTotalBlocks == null?0L:twentyFourTotalBlocks;
        log.info("查询FIL币算力按小时聚合表里近24小时所有的每小时出块份数总和出参修改格式后的：【{}】",twentyFourTotalBlocks);
        adminFirstCollectVO.setAllBlocksPerDay(twentyFourTotalBlocks);

        return adminFirstCollectVO;
    }

    /**
     * chia管理员首页-矿工统计数据
     * @return
     */
    @Override
    public AdminFirstCollectVO chiaAdminFirstAllCount() {
        AdminFirstCollectVO adminFirstCollectVO = new AdminFirstCollectVO();
        // 管理员首页-矿工统计数据-平台总资产，用的字段：挖矿账户余额, 单位FIL
        BigDecimal allBalanceMinerAccount = chiaMinerService.selectFilAllBalanceMinerAccount();
        adminFirstCollectVO.setAllBalanceMinerAccount(BigDecimalUtil.formatFour(allBalanceMinerAccount));
        // 管理员首页-矿工统计数据-平台有效算力
        BigDecimal allPowerAvailable = chiaMinerService.selectFilAllPowerAvailable();
        adminFirstCollectVO.setAllPowerAvailable(allPowerAvailable);
        // 管理员首页-矿工统计数据-活跃矿工
        Long allMinerCount = chiaMinerService.selectFilAllMinerIdCount();
        adminFirstCollectVO.setAllMinerCount(allMinerCount);
        // 管理员首页-矿工统计数据-当天出块份数
        String yesterDayDate = DateUtils.getYesterDayDateYmd();
        Long allBlocksPerDay = chiaMinerService.selectFilAllBlocksPerDay(yesterDayDate);
        adminFirstCollectVO.setAllBlocksPerDay(allBlocksPerDay);
        return adminFirstCollectVO;
    }

    /**
     * fil管理员首页-平台有效算力排行榜
     * @param yesterDayDate
     * @param basePage
     * @return
     */
    /*@Override
    public Map<String,Object> filPowerAvailablePage(String yesterDayDate,BasePage basePage) {
        // 管理员首页-矿工统计数据-平台有效算力
        BigDecimal allPowerAvailable = sysMinerInfoService.selectFilAllPowerAvailable();
        log.info("fil管理员首页-矿工统计数据-平台有效算力出参：【{}】",allPowerAvailable);
        Page<PowerAvailableFilVO> powerAvailableFilVOPage = new Page<>(basePage.getPageNum(),basePage.getPageSize());
        log.info("fil币管理员首页-平台有效算力排行榜入参yesterDayDate:【{}】",yesterDayDate);
        IPage<PowerAvailableFilVO> result = sysMinerInfoMapper.powerAvailablePage(powerAvailableFilVOPage,yesterDayDate);
        log.info("fil币管理员首页-平台有效算力排行榜出参:【{}】",JSON.toJSON(result));
        if (result != null && result.getRecords() != null && result.getRecords().size() > 0) {
            for (PowerAvailableFilVO powerAvailableFilVO:result.getRecords()) {
                log.info("根据userId查询fil币矿工信息表里的该用户所有的矿工ID入参：【{}】",powerAvailableFilVO.getUserId());
                List<String> minerIdList = sysMinerInfoService.findMinerIdByUserId(powerAvailableFilVO.getUserId());
                log.info("根据userId查询fil币矿工信息表里的该用户所有的矿工ID出参：【{}】",minerIdList);
                log.info("管理员-首页-平台有效算力排行榜-查询算力按天聚合表里的挖矿效率、算力增速入参yesterDayDate：【{}】,minerIdList:【{}】",yesterDayDate, minerIdList);
                PowerAvailableFilVO dbPowerAvailableFilVO = sysAggPowerDailyService.selectPowerAvailableByDateAndUserIdList(yesterDayDate, minerIdList,CurrencyEnum.FIL.name());
                log.info("管理员-首页-平台有效算力排行榜-查询算力按天聚合表里的挖矿效率、算力增速出参：【{}】",JSON.toJSON(dbPowerAvailableFilVO));
                if (dbPowerAvailableFilVO != null) {
                    powerAvailableFilVO.setMiningEfficiency(BigDecimalUtil.formatFour(dbPowerAvailableFilVO.getMiningEfficiency()));
                    powerAvailableFilVO.setPowerIncrease(dbPowerAvailableFilVO.getPowerIncrease());
                    powerAvailableFilVO.setTotalBlocksPerDay(powerAvailableFilVO.getTotalBlocks()-dbPowerAvailableFilVO.getTotalBlocks());
                } else {
                    powerAvailableFilVO.setMiningEfficiency(BigDecimal.ZERO);
                    powerAvailableFilVO.setPowerIncrease(BigDecimal.ZERO);
                    powerAvailableFilVO.setTotalBlocksPerDay(0L);
                }

                SysUserOut sysUserOut = new SysUserOut();
                sysUserOut.setUserId(powerAvailableFilVO.getUserId());
                log.info("查询用户姓名入参：【{}】",JSON.toJSON(sysUserOut));
                Result<SysUserOut> sysUserOutResult = userFeignClient.getUserById(sysUserOut);
                log.info("查询用户姓名出参：【{}】",JSON.toJSON(sysUserOutResult));
                if(ErrorCode.MYB_000000.getCode().equals(sysUserOutResult.getCode())){
                    powerAvailableFilVO.setUserName(sysUserOutResult.getData().getUserName());
                }
                if(powerAvailableFilVO.getPowerAvailable() != null && !allPowerAvailable.equals(BigDecimal.ZERO)) {
                    powerAvailableFilVO.setPowerAvailablePercent(powerAvailableFilVO.getPowerAvailable().divide(allPowerAvailable,2,BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100)));
                } else {
                    powerAvailableFilVO.setPowerAvailablePercent(BigDecimal.ZERO);
                }
                powerAvailableFilVO.setTotalBlockAward(BigDecimalUtil.formatFour(powerAvailableFilVO.getTotalBlockAward()));
            }
        }
        Map<String,Object> map = new HashMap<>();
        map.put("code", ErrorCode.MYB_000000.getCode());
        map.put("msg",ErrorCode.MYB_000000.getMsg());
        map.put("rows",result.getRecords());
        map.put("total",result.getTotal());
        return map;
    }*/


    /**
     * chia管理员首页-平台有效算力排行榜
     * @param yesterDayDate
     * @param basePage
     * @return
     */
    @Override
    public Map<String,Object> chiaPowerAvailablePage(String yesterDayDate,BasePage basePage) {
        // 管理员首页-矿工统计数据-平台有效算力
        BigDecimal allPowerAvailable = chiaMinerService.selectFilAllPowerAvailable();
        log.info("chia管理员首页-矿工统计数据-平台有效算力出参：【{}】",allPowerAvailable);
        Page<PowerAvailableFilVO> powerAvailableFilVOPage = new Page<>(basePage.getPageNum(),basePage.getPageSize());
        IPage<PowerAvailableFilVO> result = chiaMinerMapper.powerAvailablePage(powerAvailableFilVOPage,allPowerAvailable);
        log.info("chia币管理员首页-平台有效算力排行榜:【{}】",JSON.toJSON(result));
        if (result != null && result.getRecords() != null && result.getRecords().size() > 0) {
            for (PowerAvailableFilVO powerAvailableFilVO:result.getRecords()) {
                log.info("根据userId查询起亚币矿工信息表里的该用户所有的矿工ID入参：【{}】",powerAvailableFilVO.getUserId());
                List<String> minerIdList = chiaMinerService.findMinerIdByUserId(powerAvailableFilVO.getUserId());
                log.info("根据userId查询起亚币矿工信息表里的该用户所有的矿工ID出参：【{}】",minerIdList);
                log.info("管理员-首页-平台有效算力排行榜-查询算力按天聚合表里的挖矿效率、算力增速入参yesterDayDate：【{}】,minerIdList:【{}】",yesterDayDate, minerIdList);
                PowerAvailableFilVO dbPowerAvailableFilVO = sysAggPowerDailyService.selectPowerAvailableByDateAndUserIdList(yesterDayDate, minerIdList,CurrencyEnum.XCH.name());
                log.info("管理员-首页-平台有效算力排行榜-查询算力按天聚合表里的挖矿效率、算力增速出参：【{}】",JSON.toJSON(dbPowerAvailableFilVO));
                if (dbPowerAvailableFilVO != null) {
                    powerAvailableFilVO.setMiningEfficiency(BigDecimalUtil.formatFour(dbPowerAvailableFilVO.getMiningEfficiency()));
                    powerAvailableFilVO.setPowerIncrease(dbPowerAvailableFilVO.getPowerIncrease());
                } else {
                    powerAvailableFilVO.setMiningEfficiency(BigDecimal.ZERO);
                    powerAvailableFilVO.setPowerIncrease(BigDecimal.ZERO);
                }

                SysUserOut sysUserOut = new SysUserOut();
                sysUserOut.setUserId(powerAvailableFilVO.getUserId());
                log.info("查询用户姓名入参：【{}】",JSON.toJSON(sysUserOut));
                Result<SysUserOut> sysUserOutResult = userFeignClient.getUserById(sysUserOut);
                log.info("查询用户姓名出参：【{}】",JSON.toJSON(sysUserOutResult));
                if(ErrorCode.MYB_000000.getCode().equals(sysUserOutResult.getCode())){
                    powerAvailableFilVO.setUserName(sysUserOutResult.getData().getUserName());
                }
                powerAvailableFilVO.setPowerAvailablePercent(BigDecimalUtil.formatTwo(powerAvailableFilVO.getPowerAvailablePercent().multiply(new BigDecimal(100))));
                powerAvailableFilVO.setTotalBlockAward(BigDecimalUtil.formatFour(powerAvailableFilVO.getTotalBlockAward()));
            }
        }
        Map<String,Object> map = new HashMap<>();
        map.put("code", ErrorCode.MYB_000000.getCode());
        map.put("msg",ErrorCode.MYB_000000.getMsg());
        map.put("rows",powerAvailableFilVOPage.getRecords());
        map.put("total",powerAvailableFilVOPage.getTotal());
        return map;
    }
}

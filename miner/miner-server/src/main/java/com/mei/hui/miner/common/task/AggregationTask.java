package com.mei.hui.miner.common.task;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.mei.hui.miner.entity.SysAggAccountDaily;
import com.mei.hui.miner.entity.SysAggPowerDaily;
import com.mei.hui.miner.entity.SysMinerInfo;
import com.mei.hui.miner.service.ISysAggAccountDailyService;
import com.mei.hui.miner.service.ISysAggPowerDailyService;
import com.mei.hui.miner.service.ISysMinerInfoService;
import com.mei.hui.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.List;

@Configuration
@EnableScheduling
public class AggregationTask {
    private static final Logger log = LoggerFactory.getLogger(AggregationTask.class);

    @Autowired
    private ISysAggAccountDailyService sysAggAccountDailyService;

    @Autowired
    private ISysAggPowerDailyService sysAggPowerDailyService;

    @Autowired
    private ISysMinerInfoService sysMinerInfoService;
    /**
     * 账户按天聚合
     */
    //或直接指定时间间隔，例如：5秒
    @Scheduled(cron = "0 0 0 */1 * ?")
    public void dailyAccount() {
        log.info("======================AggregationTask-start===================");
        SysMinerInfo sysMinerInfo = new SysMinerInfo();
        int pageNum = 1;
        int pageSize = 100;
        while (true) {
            PageHelper.startPage(pageNum,pageSize, "id");
            log.info("获取旷工,入参: pageNum = {},pageSize = {}",pageNum,pageSize);
            List<SysMinerInfo> list = sysMinerInfoService.findMinerInfoList(sysMinerInfo);
            log.info("获取旷工,出参: {}", JSON.toJSONString(list));
            for (SysMinerInfo info : list) {
                log.info("旷工信息:{}",JSON.toJSONString(info));
                insertAccount(info);
                insertPower(info);
            }
            if (list.size() < pageSize) {
                break;
            } else {
                pageNum ++;
            }
        }
        log.info("======================AggregationTask-end===================");
    }

    private void insertPower(SysMinerInfo info){
        log.info("算力聚合表");
        String date = DateUtils.getDate();
        //当前日期转换成YYYY-mm-dd 格式
        String dateStr = DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, DateUtils.parseDate(date));
        log.info("查询当天算力聚合表,入参:minerId = {},date={}",info.getMinerId(),dateStr);
        SysAggPowerDaily today = sysAggPowerDailyService.selectSysAggPowerDailyByMinerIdAndDate(info.getMinerId(),dateStr);
        log.info("查询当天算力聚合表,出参:{}",JSON.toJSONString(today));
        if (today == null) {
            String yesterDateStr = DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, DateUtils.addDays(DateUtils.parseDate(date), -1));
            log.info("查询昨天算力聚合表,入参:minerId ={},date={}",info.getMinerId(),yesterDateStr);
            SysAggPowerDaily yesterday = sysAggPowerDailyService.selectSysAggPowerDailyByMinerIdAndDate(info.getMinerId(),yesterDateStr);
            log.info("查询昨天算力聚合表,出参:{}",JSON.toJSONString(yesterDateStr));
            SysAggPowerDaily sysAggPowerDaily = new SysAggPowerDaily();
            sysAggPowerDaily.setMinerId(info.getMinerId());
            sysAggPowerDaily.setDate(date);
            sysAggPowerDaily.setPowerAvailable(info.getPowerAvailable().longValue());
            if (yesterday != null) {
                sysAggPowerDaily.setPowerIncrease(info.getPowerAvailable().longValue() - yesterday.getPowerAvailable());
            } else {
                sysAggPowerDaily.setPowerIncrease(info.getPowerAvailable().longValue());
            }
            log.info("算力聚合表插入数据,入参:{}",JSON.toJSONString(sysAggPowerDaily));
            int result = sysAggPowerDailyService.insertSysAggPowerDaily(sysAggPowerDaily);
            log.info("算力聚合表插入数据,返回值:{}",result);
        }
    }

    /**
     *
     * @param info
     */
    private void insertAccount(SysMinerInfo info) {
        log.info("账户聚合表");
        String date = DateUtils.getDate();
        log.info("查询账户聚合表,入参:minerId = {},date={}",info.getMinerId(),date);
        SysAggAccountDaily data = sysAggAccountDailyService.selectSysAggAccountDailyByMinerIdAndDate(info.getMinerId(),date);
        log.info("查询账户聚合表,出参:",JSON.toJSONString(data));
        if (data == null) {
            SysAggAccountDaily sysAggAccountDaily = new SysAggAccountDaily();
            sysAggAccountDaily.setMinerId(info.getMinerId());
            sysAggAccountDaily.setDate(date);
            sysAggAccountDaily.setBalanceAccount(info.getBalanceMinerAccount());
            sysAggAccountDaily.setBalanceAvailable(info.getBalanceMinerAvailable());
            sysAggAccountDaily.setSectorPledge(info.getSectorPledge());
            sysAggAccountDaily.setLockAward(info.getLockAward());
            log.info("账户聚合表新增数据,入参:{}",JSON.toJSONString(sysAggAccountDaily));
            int result = sysAggAccountDailyService.insertSysAggAccountDaily(sysAggAccountDaily);
            log.info("账户聚合表新增数据,返回值:{}",result);
        }
    }
}

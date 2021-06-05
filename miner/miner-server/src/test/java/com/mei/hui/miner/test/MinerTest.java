//package com.mei.hui.miner.test;
//
//import cc.block.data.api.BlockccApiClientFactory;
//import cc.block.data.api.BlockccApiRestClient;
//import cc.block.data.api.domain.BlockccResponse;
//import cc.block.data.api.domain.market.Price;
//import cc.block.data.api.domain.market.request.PriceParam;
//import com.alibaba.fastjson.JSON;
//import com.mei.hui.config.AESUtil;
//import com.mei.hui.config.redisConfig.RedisUtil;
//import com.mei.hui.miner.MinerApplication;
//import com.mei.hui.miner.common.enums.CurrencyEnum;
//import com.mei.hui.miner.entity.SysAggPowerDaily;
//import com.mei.hui.miner.entity.SysMinerInfo;
//import com.mei.hui.miner.mapper.SysMinerInfoMapper;
//import com.mei.hui.miner.service.ISysAggPowerDailyService;
//import com.mei.hui.miner.service.ISysMinerInfoService;
//import com.mei.hui.user.feign.feignClient.UserFeignClient;
//import com.mei.hui.user.feign.vo.SysUserOut;
//import com.mei.hui.util.DateUtils;
//import com.mei.hui.util.Result;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.junit4.SpringRunner;
//
//import java.math.BigDecimal;
//import java.util.Date;
//import java.util.List;
//
//@RunWith(SpringRunner.class)
//@SpringBootTest(classes = MinerApplication .class)
//@Slf4j
//public class MinerTest {
//
//    @Autowired
//    private RedisUtil redisUtil;
//    @Autowired
//    private ISysMinerInfoService sysMinerInfoService;
//    @Autowired
//    private SysMinerInfoMapper sysMinerInfoMapper;
//
//    @Autowired
//    private UserFeignClient userFeignClient;
//    @Autowired
//    private ISysAggPowerDailyService sysAggPowerDailyService;
//
//    @Test
//    public void entry(){
//        String token = AESUtil.encrypt("1");
//        log.info("token = {}",token);
//    }
//
//    @Test
//    public void testRedis() {
//        redisUtil.set("testRedisKey","testRedisValue");
//        System.out.print(redisUtil.get("testRedisKey"));
//    }
//    @Test
//    public void testMydql(){
//        SysMinerInfo miner = sysMinerInfoMapper.selectById(29);
//        miner.setDeadlineIndex(100000L);
//        miner.setDeadlineSectors(10000L);
//        miner.setProvingPeriodStart(100000L);
//        miner.setId(30L);
//       // sysMinerInfoService.updateSysMinerInfo(miner);
//        sysMinerInfoService.insertSysMinerInfo(miner);
//    }
//
//    @Test
//    public  void testGetUserById(){
//        SysUserOut sysUserOut = new SysUserOut();
//        sysUserOut.setUserId(5L);
//        Result<SysUserOut> sysUserOutResult = userFeignClient.getUserById(sysUserOut);
//        System.out.print(JSON.toJSON(sysUserOutResult));
//    }
//
//    @Test
//    public  void CurrencyTest(){
//        String date = DateUtils.getDate();
//        String yesterDateStr = DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, DateUtils.addDays(DateUtils.parseDate(date), -1));
//        System.out.print(yesterDateStr);
//    }
//    @Autowired
// private BlockccApiRestClient client;
//    @Test
//    public void AggTest(){
//        MarketClient marketClient = MarketClient.create(new HuobiOptions());
//        Long startNano = System.nanoTime();
//        marketClient.getCandlestick(CandlestickRequest.builder().symbol(symbol).interval(CandlestickIntervalEnum.MIN15).size(5).build());
//        Long endNano = System.nanoTime();
//        NetworkLatency networkLatency = ConnectionFactory.getLatencyDebugQueue().poll();
//        print(networkLatency, startNano, endNano);
//
//        log.info(JSON.toJSONString("fil币信息："+result.getContent().get(0)));
//        log.info(JSON.toJSONString("chia币信息："+result1.getContent().get(0)));
//    }
//
//    public static void print(NetworkLatency networkLatency, Long startNanoTime, Long endNanoTime) {
//
//        long nanoToMicrosecond = 1000;
//
//        Long prepareCost = (networkLatency.getStartNanoTime() - startNanoTime) / nanoToMicrosecond;
//        Long deserializationCost = (endNanoTime - networkLatency.getEndNanoTime()) / nanoToMicrosecond;
//        Long networkCost = (networkLatency.getEndNanoTime() - networkLatency.getStartNanoTime()) / nanoToMicrosecond;
//        Long totalCost = (endNanoTime - startNanoTime) / nanoToMicrosecond;
//        Long innerCost = (totalCost - networkCost);
//
//        StringBuilder stringBuilder = new StringBuilder();
//        stringBuilder.append("path:").append(networkLatency.getPath())
//                .append(" prepare:").append(prepareCost)
//                .append(" deserializtion:").append(deserializationCost)
//                .append(" network:").append(networkCost)
//                .append(" inner:").append(innerCost)
//                .append(" total:").append(totalCost);
//
////    stringBuilder.append("|").append(networkLatency.getPath())
////        .append(" |").append(prepareCost)
////        .append(" |").append(deserializationCost)
////        .append(" |").append(networkCost)
////        .append(" |").append(innerCost)
////        .append(" |").append(totalCost);
//
//        System.out.println(stringBuilder.toString());
//
//
//    }
//
//
//
//}

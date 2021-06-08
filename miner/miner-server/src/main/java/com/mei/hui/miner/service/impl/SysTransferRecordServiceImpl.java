package com.mei.hui.miner.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mei.hui.config.HttpRequestUtil;
import com.mei.hui.miner.common.MinerError;
import com.mei.hui.miner.common.enums.TransferRecordStatusEnum;
import com.mei.hui.miner.entity.*;
import com.mei.hui.miner.mapper.*;
import com.mei.hui.miner.model.*;
import com.mei.hui.miner.service.CurrencyRateService;
import com.mei.hui.miner.service.ISysTransferRecordService;
import com.mei.hui.user.feign.feignClient.UserFeignClient;
import com.mei.hui.user.feign.vo.FindSysUserListInput;
import com.mei.hui.user.feign.vo.FindSysUsersByNameBO;
import com.mei.hui.user.feign.vo.FindSysUsersByNameVO;
import com.mei.hui.user.feign.vo.SysUserOut;
import com.mei.hui.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统划转记录Service业务层处理
 * 
 * @author ruoyi
 * @date 2021-03-08
 */
@Service
@Slf4j
public class SysTransferRecordServiceImpl implements ISysTransferRecordService {
    @Autowired
    private SysTransferRecordMapper sysTransferRecordMapper;
    @Autowired
    private UserFeignClient userFeignClient;
    @Autowired
    private SysMinerInfoMapper sysMinerInfoMapper;
    @Autowired
    private MrAggWithdrawMapper mrAggWithdrawMapper;
    @Autowired
    private ChiaMinerMapper chiaMinerMapper;
    @Autowired
    private SysReceiveAddressMapper sysReceiveAddressMapper;
    @Autowired
    private CurrencyRateService currencyRateService;


    /**
     * 查询系统划转记录
     * @param id 系统划转记录ID
     * @return 系统划转记录
     */
    @Override
    public SysTransferRecord selectSysTransferRecordById(Long id)
    {
        return sysTransferRecordMapper.selectSysTransferRecordById(id);
    }

    /**
     * 查询系统划转记录列表
     * 
     * @param sysTransferRecord 系统划转记录
     * @return 系统划转记录
     */
    @Override
    public List<SysTransferRecord> selectSysTransferRecordList(SysTransferRecord sysTransferRecord)
    {
        return sysTransferRecordMapper.selectSysTransferRecordList(sysTransferRecord);
    }

    /**
     * 新增系统划转记录
     * 
     * @param sysTransferRecord 系统划转记录
     * @return 结果
     */
    @Override
    public int insertSysTransferRecord(SysTransferRecord sysTransferRecord)
    {
        sysTransferRecord.setCreateTime(LocalDateTime.now());
        return sysTransferRecordMapper.insertSysTransferRecord(sysTransferRecord);
    }

    /**
     * 修改系统划转记录，如果审核通过，则修改用户收益汇总表
     * 
     * @param sysTransferRecord 系统划转记录
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateSysTransferRecord(SysTransferRecord sysTransferRecord){
        SysTransferRecord transferRecord = sysTransferRecordMapper.selectById(sysTransferRecord.getId());
        if(transferRecord == null){
            throw MyException.fail(MinerError.MYB_222222.getCode(),"提交记录不存在");
        }
        if(transferRecord.getStatus() != 0){
            throw MyException.fail(MinerError.MYB_222222.getCode(),"审核已经完成");
        }
        sysTransferRecord.setUpdateTime(LocalDateTime.now());
        /**
         * 修改提现记录状态
         */
        log.info("修改提现记录表状态:{}",JSON.toJSONString(sysTransferRecord));
        int update = sysTransferRecordMapper.updateSysTransferRecord(sysTransferRecord);
        log.info("修改提现记录表状态,结果:{}",update);
        if(update < 0){
            throw MyException.fail(MinerError.MYB_222222.getCode(),"修改提现记录状态失败");
        }
        /**
         * 如果是提现成功，则修改提现汇总表
         */
        Long userId = transferRecord.getUserId();
        String type = transferRecord.getType();
        if(sysTransferRecord.getStatus() == 1){
            LambdaQueryWrapper<MrAggWithdraw> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(MrAggWithdraw::getSysUserId,userId);
            queryWrapper.eq(MrAggWithdraw::getType,type);
            log.info("查询提现汇总表记录,userId={}",userId);
            List<MrAggWithdraw> aggWithdraws = mrAggWithdrawMapper.selectList(queryWrapper);
            log.info("查询提现汇总表记录,结果:{}",JSON.toJSONString(aggWithdraws));
            if(aggWithdraws.size() == 0){
                log.info("新增提现汇总信息");
                MrAggWithdraw insertAggWithdraw = MrAggWithdraw.builder().sysUserId(userId).takeTotalMony(transferRecord.getAmount())
                        .type(type).tatalCount(1).totalFee(transferRecord.getFee()).build();
                mrAggWithdrawMapper.insert(insertAggWithdraw);
            }else{
                log.info("更新提现汇总信息");
                MrAggWithdraw mrAggWithdraw = aggWithdraws.get(0);
                mrAggWithdraw.setTakeTotalMony(mrAggWithdraw.getTakeTotalMony().add(transferRecord.getAmount()));
                mrAggWithdraw.setTatalCount(mrAggWithdraw.getTatalCount() + 1);
                mrAggWithdraw.setTotalFee(mrAggWithdraw.getTotalFee().add(transferRecord.getFee()));
                mrAggWithdrawMapper.updateById(mrAggWithdraw);
            }
        }
        return 1;
    }

    /**
     * 批量删除系统划转记录
     * 
     * @param ids 需要删除的系统划转记录ID
     * @return 结果
     */
    @Override
    public int deleteSysTransferRecordByIds(Long[] ids)
    {
        return sysTransferRecordMapper.deleteSysTransferRecordByIds(ids);
    }

    /**
     * 删除系统划转记录信息
     * 
     * @param id 系统划转记录ID
     * @return 结果
     */
    @Override
    public int deleteSysTransferRecordById(Long id)
    {
        return sysTransferRecordMapper.deleteSysTransferRecordById(id);
    }

    /**
     * 获取用户已提取收益
     *
     * @param userId 用户ID
     * @return 结果
     */
    @Override
    public Double selectTotalWithdrawByUserId(Long userId)
    {
        return sysTransferRecordMapper.selectTotalWithdrawByUserId(userId);
    }

    /**
     * 总手续费收益
     * @return
     */
    @Override
    public List<TransferRecordFeeVO> selectTotalEarning() {
        return sysTransferRecordMapper.selectTotalEarning();
    }

    /**
     * 今日手续费收益
     * @return
     */
    @Override
    public List<TransferRecordFeeVO> selectTodayEarning() {
        return sysTransferRecordMapper.selectTodayEarning();
    }

    @Override
    public Map<String,Object> findTransferRecords(SysTransferRecord sysTransferRecord) {
        Long currencyId = HttpRequestUtil.getCurrencyId();
        if (StringUtils.isEmpty(sysTransferRecord.getMinerId())) {
            throw MyException.fail(MinerError.MYB_222222.getCode(), "minerId不能为空值");
        }
        LambdaQueryWrapper<SysTransferRecord> queryWrapper = new LambdaQueryWrapper<>();
        if("createTime".equals(sysTransferRecord.getCloumName())){
            if(sysTransferRecord.isAsc()){
                queryWrapper.orderByAsc(SysTransferRecord::getCreateTime);
            }else{
                queryWrapper.orderByDesc(SysTransferRecord::getCreateTime);
            }
        }else if("amount".equals(sysTransferRecord.getCloumName())){
            if(sysTransferRecord.isAsc()){
                queryWrapper.orderByAsc(SysTransferRecord::getAmount);
            }else{
                queryWrapper.orderByDesc(SysTransferRecord::getAmount);
            }
        }else if("fee".equals(sysTransferRecord.getCloumName())){
            if(sysTransferRecord.isAsc()){
                queryWrapper.orderByAsc(SysTransferRecord::getFee);
            }else{
                queryWrapper.orderByDesc(SysTransferRecord::getFee);
            }
        }else{
            queryWrapper.orderByDesc(SysTransferRecord::getCreateTime);
        }
        queryWrapper.eq(SysTransferRecord::getUserId,HttpRequestUtil.getUserId());
        queryWrapper.eq(SysTransferRecord::getMinerId,sysTransferRecord.getMinerId());
        queryWrapper.eq(SysTransferRecord::getType,CurrencyEnum.getCurrency(currencyId).name());
        IPage<SysTransferRecord> page = sysTransferRecordMapper.selectPage(new Page<>(sysTransferRecord.getPageNum(), sysTransferRecord.getPageSize()), queryWrapper);
        //批量获取用户
        List<Long> userids = page.getRecords().stream().map(v -> {
            return v.getUserId();
        }).collect(Collectors.toList());
        Map<Long, SysUserOut> userMaps = sysUserToMap(userids);

        page.getRecords().stream().forEach(v -> {
            SysUserOut user = userMaps.get(v.getUserId());
            v.setUserName(user.getUserName());
        });
        /**
         * 组装返回信息
         */
        Map<String,Object> map = new HashMap<>();
        map.put("code", ErrorCode.MYB_000000.getCode());
        map.put("msg", ErrorCode.MYB_000000.getMsg());
        map.put("rows", page.getRecords());
        map.put("total", page.getTotal());
        return map;
    }

    /**
     * 查询系统划转记录列表,加UserName
     *系统划转记录
     * @return 系统划转记录集合
     */
    @Override
    @Transactional
    public Map<String,Object> selectSysTransferRecordListUserName(AggWithdrawBO aggWithdrawBO){
        LambdaQueryWrapper<SysTransferRecord> queryWrapper = new LambdaQueryWrapper<>();
        if("amount".equals(aggWithdrawBO.getCloumName())){
            if(aggWithdrawBO.isAsc()){
                //true升序
                queryWrapper.orderByAsc(SysTransferRecord::getAmount);
            }else {
                //降序
                queryWrapper.orderByDesc(SysTransferRecord::getAmount);
            }
        } else if("fee".equals(aggWithdrawBO.getCloumName())){
            if(aggWithdrawBO.isAsc()){
                //true升序
                queryWrapper.orderByAsc(SysTransferRecord::getFee);
            }else {
                //降序
                queryWrapper.orderByDesc(SysTransferRecord::getFee);
            }
        } else {
            queryWrapper.orderByDesc(SysTransferRecord::getCreateTime);
        }

        //用于入参模块模糊查询，获取用户id
        String userName = aggWithdrawBO.getUserName();
        if (StringUtils.isNotEmpty(userName)) {
            FindSysUsersByNameBO bo = new FindSysUsersByNameBO();
            bo.setName(userName);
            log.info("模糊查询用户id集合：【{}】",JSON.toJSON(bo));
            Result<List<FindSysUsersByNameVO>> userResult = userFeignClient.findSysUsersByName(bo);
            log.info("模糊查询用户id集合结果:{}", JSON.toJSONString(userResult));
            List<Long> idList = new ArrayList<>();
            if(ErrorCode.MYB_000000.getCode().equals(userResult.getCode()) && userResult.getData().size() > 0){
                idList = userResult.getData().stream().map(v ->v.getUserId()).collect(Collectors.toList());
                // id去重
                Set<Long> idsSet = new HashSet<>(idList);
                queryWrapper.in(SysTransferRecord::getUserId,new ArrayList<Long>(idsSet));
            } else {
                Map<String,Object> result = new HashMap<>();
                result.put("code", ErrorCode.MYB_000000.getCode());
                result.put("msg", ErrorCode.MYB_000000.getMsg());
                result.put("rows", new ArrayList());
                result.put("total", 0);
                return result;
            }
        }

        // 查询条件币种
        Long currencyId = aggWithdrawBO.getCurrencyId();
        if (currencyId != null) {
            CurrencyEnum currencyEnum = CurrencyEnum.getCurrency(currencyId);
            if (currencyEnum == null) {
                throw MyException.fail(MinerError.MYB_222222.getCode(),"入参币种不存在");
            }
            String currencyType = currencyEnum.name();
            queryWrapper.eq(SysTransferRecord::getType,currencyType);
        }

        if (aggWithdrawBO.getStatus() != null){
            queryWrapper.eq(SysTransferRecord::getStatus,aggWithdrawBO.getStatus());
        }

        log.info("根据 entity 条件，查询全部记录（并翻页）入参：【{}】",JSON.toJSON(queryWrapper));
        IPage<SysTransferRecord> page = sysTransferRecordMapper.selectPage(new Page<>(aggWithdrawBO.getPageNum(), aggWithdrawBO.getPageSize()), queryWrapper);
        List<Long> ids = page.getRecords().stream().map(v -> v.getUserId()).collect(Collectors.toList());
        page.getRecords().stream().filter(v-> page.getRecords() != null && page.getRecords().size() > 0).forEach(v->{
            v.setType(v.getType());
        });
        /**
         * 查询用户
         */
        Map<Long,SysUserOut> map = new HashMap<>();
        if(ids.size() > 0){
            FindSysUserListInput findSysUserListInput = new FindSysUserListInput();
            findSysUserListInput.setUserIds(ids);
            log.info("批量获取用户入参：【{}】",JSON.toJSON(findSysUserListInput));
            Result<List<SysUserOut>> users = userFeignClient.findSysUserList(findSysUserListInput);
            log.info("批量获取用户出参：【{}】",JSON.toJSON(users));
            users.getData().stream().forEach(v->map.put(v.getUserId(),v));
        }
        /**
         * 组装返回信息
         */
        if(map.size() > 0){
            page.getRecords().stream().forEach(v->v.setUserName(map.get(v.getUserId()).getUserName()));
        }

        Map<String,Object> result = new HashMap<>();
        result.put("code", ErrorCode.MYB_000000.getCode());
        result.put("msg", ErrorCode.MYB_000000.getMsg());
        result.put("rows", page.getRecords());
        result.put("total", page.getTotal());
        return result;
    }

    public Map<Long,SysUserOut>  sysUserToMap(List<Long> userids){
        FindSysUserListInput input = new FindSysUserListInput();
        input.setUserIds(userids);
        log.info("请求用户模块");
        Result<List<SysUserOut>> result = userFeignClient.findSysUserList(input);
        log.info("用户模块返回值:{}", JSON.toJSONString(result));
        Map<Long,SysUserOut> map = new HashMap<>();
        if(ErrorCode.MYB_000000.getCode().equals(result.getCode())){
            List<SysUserOut> users = result.getData();
            users.stream().forEach(v->{
                map.put(v.getUserId(),v);
            });
        }
        return map;
    }


    /**
     * 用户提币：
     * 1、先校验现有余额是否 大于 将要提取的fil, 余额 - 带提币中的fil > 即将提取的fil
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result withdraw(SysTransferRecordWrap sysTransferRecordWrap){
        if(sysTransferRecordWrap.getMinerId() == null){
            throw MyException.fail(MinerError.MYB_222222.getCode(),"请选择旷工");
        }
        Long userId = HttpRequestUtil.getUserId();
        Long currencyId = HttpRequestUtil.getCurrencyId();
        String currencyType = CurrencyEnum.getCurrency(currencyId).name();
        String minerId = sysTransferRecordWrap.getMinerId();
        /**
         * 获取费率
         */
        Map<String, BigDecimal> rateMap = currencyRateService.getUserRateMap(userId);

        /**
         * 一：提取金额 < 可提现金额 - 提币中 金额
         */
        BigDecimal feeRate = new BigDecimal(5);
        //获取可提现金额
        BigDecimal balanceMinerAvailable = BigDecimal.ZERO;
        if(CurrencyEnum.FIL.getCurrencyId().equals(currencyId)){//fil 币
            LambdaQueryWrapper<SysMinerInfo> wrapper = new LambdaQueryWrapper();
            wrapper.eq(SysMinerInfo::getMinerId,minerId);
            wrapper.eq(SysMinerInfo::getUserId,userId);
            log.info("查询fil旷工信息,入参:{}",wrapper.toString());
            List<SysMinerInfo> miners = sysMinerInfoMapper.selectList(wrapper);
            log.info("查询fil旷工信息,出参:{}",JSON.toJSONString(miners));
            if(miners.size() == 0){
                throw MyException.fail(MinerError.MYB_222222.getCode(),"旷工不存在");
            }
            SysMinerInfo sysMinerInfo = miners.get(0);
            balanceMinerAvailable = sysMinerInfo.getBalanceMinerAvailable();
            //费率
            feeRate = rateMap.get(CurrencyEnum.FIL.name());

        }else if(CurrencyEnum.XCH.getCurrencyId().equals(currencyId)){//起亚币
            ChiaMiner chiaMiner = new ChiaMiner();
            chiaMiner.setUserId(userId);
            chiaMiner.setMinerId(minerId);
            QueryWrapper<ChiaMiner> queryWrapper = new QueryWrapper<>();
            queryWrapper.setEntity(chiaMiner);
            log.info("查询chia旷工信息,入参:{}",JSON.toJSON(chiaMiner));
            List<ChiaMiner> chiaMinerList = chiaMinerMapper.selectList(queryWrapper);
            log.info("查询chia旷工信息,出参:{}",JSON.toJSON(chiaMinerList));
            if(chiaMinerList.size() == 0){
                throw MyException.fail(MinerError.MYB_222222.getCode(),"旷工不存在");
            }
            balanceMinerAvailable =chiaMinerList.get(0).getBalanceMinerAccount();
            //费率
            feeRate = rateMap.get(CurrencyEnum.XCH.name());
        }

        //获取提币中的金额
        LambdaQueryWrapper<SysTransferRecord> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(SysTransferRecord::getUserId, userId);
        queryWrapper.eq(SysTransferRecord::getStatus,0);
        queryWrapper.eq(SysTransferRecord::getMinerId, minerId);
        queryWrapper.eq(SysTransferRecord::getType, currencyType);
        log.info("获取提币中的金额,入参:{}",queryWrapper.toString());
        List<SysTransferRecord> transferRecord = sysTransferRecordMapper.selectList(queryWrapper);
        log.info("获取提币中的金额,出参:{}",JSON.toJSONString(transferRecord));
        BigDecimal gettingEarning = new BigDecimal(0);
        BigDecimal gettingFee = new BigDecimal(0);
        for(SysTransferRecord record : transferRecord){
            gettingEarning = gettingEarning.add(record.getAmount());
            gettingFee = gettingFee.add(record.getFee());
        }
        log.info("提币中的金额:{}",gettingEarning);
        //提取金额 < 可提现金额 - （提币中金额）
        BigDecimal account = balanceMinerAvailable.subtract(gettingEarning).subtract(sysTransferRecordWrap.getAmount()).subtract(gettingFee);
        if(account.compareTo(new BigDecimal(0)) < 0){
            throw MyException.fail(MinerError.MYB_222222.getCode(),"金额不够");
        }
       /* Result<SysUserOut> userResult = userFeignClient.getLoginUser();
        if(!ErrorCode.MYB_000000.getCode().equals(userResult.getCode())){
            throw MyException.fail(userResult.getCode(),userResult.getMsg());
        }*/
        BigDecimal fee = feeRate.multiply(sysTransferRecordWrap.getAmount()).divide(new BigDecimal(100));
        sysTransferRecordWrap.setFee(fee);
        //实际到账金额 = 提币金额 - 平台佣金
        sysTransferRecordWrap.setAmount(sysTransferRecordWrap.getAmount().subtract(fee));
        // 通过用户ID、货币ID查询用户货币地址表中的地址
        QueryWrapper<SysReceiveAddress> sysReceiveAddressQueryWrapper = new QueryWrapper<>();
        SysReceiveAddress sysReceiveAddress = new SysReceiveAddress();
        sysReceiveAddress.setUserId(userId);
        sysReceiveAddress.setCurrencyId(currencyId);
        sysReceiveAddressQueryWrapper.setEntity(sysReceiveAddress);
        List<SysReceiveAddress> sysReceiveAddressList = sysReceiveAddressMapper.selectList(sysReceiveAddressQueryWrapper);
        if (sysReceiveAddressList == null || sysReceiveAddressList.size() < 1) {
            throw MyException.fail(MinerError.MYB_222222.getCode(),"提币地址不存在");
        }
        String address = sysReceiveAddressList.get(0).getAddress();
        log.info("sysTransferRecordWrap.getToAddress():【{}】,address:【{}】",sysTransferRecordWrap.getToAddress(),address);
        if (!sysTransferRecordWrap.getToAddress().equals(address)){
            throw MyException.fail(MinerError.MYB_222222.getCode(),"提币地址错误");
        }

        //记录提币申请
        SysTransferRecord sysTransferRecord = new SysTransferRecord();
        BeanUtils.copyProperties(sysTransferRecordWrap, sysTransferRecord);
        sysTransferRecord.setUserId(userId);
        sysTransferRecord.setCreateTime(LocalDateTime.now());
        sysTransferRecord.setUpdateTime(LocalDateTime.now());
        sysTransferRecord.setStatus(0);
        sysTransferRecord.setMinerId(sysTransferRecordWrap.getMinerId());
        sysTransferRecord.setCreateTime(LocalDateTime.now());
        sysTransferRecord.setType(currencyType);
        log.info("记录提币申请：【{}】", JSON.toJSON(sysTransferRecord));
        int rows = sysTransferRecordMapper.insert(sysTransferRecord);

        return rows > 0 ? Result.OK : Result.fail(MinerError.MYB_222222.getCode(),"失败");
    }

    /**
     * 查询用户收益fil
     * @param input
     * @return
     */
    @Override
    public Result getUserEarning(GetUserEarningInput input){
        Long userId = HttpRequestUtil.getUserId();
        String minerId = input.getMinerId();
        if(StringUtils.isEmpty(minerId)){
            throw MyException.fail(MinerError.MYB_222222.getCode(),"minerId 为空");
        }
        EarningVo earningVo = new EarningVo(0.0, 0.0, 0.0, 0.0);
        /**
         *获取旷工信息
         */
        log.info("查询旷工信息,userId ={},minerId={}",userId,minerId);
        LambdaQueryWrapper<SysMinerInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysMinerInfo::getMinerId,minerId);
        queryWrapper.eq(SysMinerInfo::getUserId,userId);
        List<SysMinerInfo> miners = sysMinerInfoMapper.selectList(queryWrapper);
        log.info("旷工信息查询结果:{}",JSON.toJSONString(miners));
        if(miners == null || miners.size() == 0){
            return Result.success(earningVo);
        }
        SysMinerInfo miner = miners.get(0);
        /**
         * 获取提币成功状态的提取记录
         */
        LambdaQueryWrapper<SysTransferRecord> lambdaQueryWrapper = new LambdaQueryWrapper();
        lambdaQueryWrapper.eq(SysTransferRecord::getStatus,1);//提币成功
        lambdaQueryWrapper.eq(SysTransferRecord::getUserId,userId);
        lambdaQueryWrapper.eq(SysTransferRecord::getMinerId,minerId);
        lambdaQueryWrapper.eq(SysTransferRecord::getType,CurrencyEnum.FIL.name());
        log.info("查询提币完成的记录");
        List<SysTransferRecord> transfers = sysTransferRecordMapper.selectList(lambdaQueryWrapper);
        log.info("提币完成的记录查询结果:{}",JSON.toJSONString(transfers));
        //已提取收益
        BigDecimal totalWithdraw = BigDecimal.ZERO;
        for(SysTransferRecord record : transfers ) {
            totalWithdraw = totalWithdraw.add(record.getAmount());
        }
        earningVo.setTotalWithdraw(BigDecimalUtil.formatFour(totalWithdraw).doubleValue());
        /**
         * 获取所有可提取币
         */
        earningVo.setTotalEarning(BigDecimalUtil.formatFour(miner.getTotalBlockAward()).doubleValue());
        earningVo.setTotalLockAward(BigDecimalUtil.formatFour(miner.getLockAward()).doubleValue());
        earningVo.setAvailableEarning(BigDecimalUtil.formatFour(miner.getBalanceMinerAvailable()).doubleValue());
        //添加 正在提币中的数量
        LambdaQueryWrapper<SysTransferRecord> drawingWrapper = new LambdaQueryWrapper();
        drawingWrapper.eq(SysTransferRecord::getStatus,0);//提币中
        drawingWrapper.eq(SysTransferRecord::getUserId,userId);
        drawingWrapper.eq(SysTransferRecord::getMinerId,minerId);
        lambdaQueryWrapper.eq(SysTransferRecord::getType,CurrencyEnum.FIL.name());
        log.info("查询提币中的记录");
        List<SysTransferRecord> transferRecords = sysTransferRecordMapper.selectList(drawingWrapper);
        log.info("提币中的记录查询结果:{}",JSON.toJSONString(transfers));
        BigDecimal drawing = BigDecimal.ZERO;
        for(SysTransferRecord record : transferRecords ) {
            drawing = drawing.add(record.getAmount()).add(record.getFee());
        }
        earningVo.setDrawingEarning(BigDecimalUtil.formatFour(drawing).doubleValue());
        return Result.success(earningVo);
    }
    /**
     * 查询用户收益chia
     * @param input
     * @return
     */
    @Override
    public Result getUserChiaEarning(GetUserEarningInput input){
        Long userId = HttpRequestUtil.getUserId();
        String minerId = input.getMinerId();
        if(StringUtils.isEmpty(minerId)){
            throw MyException.fail(MinerError.MYB_222222.getCode(),"minerId 为空");
        }
        EarningChiaVo earningVo = new EarningChiaVo(0.0, 0.0);
        /**
         *获取旷工信息
         */
        log.info("查询旷工信息,userId ={},minerId={}",userId,minerId);
        LambdaQueryWrapper<ChiaMiner> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChiaMiner::getMinerId,minerId);
        queryWrapper.eq(ChiaMiner::getUserId,userId);
        List<ChiaMiner> miners = chiaMinerMapper.selectList(queryWrapper);
        log.info("旷工信息查询结果:{}",JSON.toJSONString(miners));
        if(miners == null || miners.size() == 0){
            return Result.success(earningVo);
        }
        ChiaMiner miner = miners.get(0);
        earningVo.setAvailableEarning(BigDecimalUtil.formatFour(miner.getBalanceMinerAccount()).doubleValue());
        /**
         * 获取已提取收益
         */
        LambdaQueryWrapper<SysTransferRecord> lambdaQueryWrapper = new LambdaQueryWrapper();
        lambdaQueryWrapper.ne(SysTransferRecord::getStatus,2);//提币成功
        lambdaQueryWrapper.eq(SysTransferRecord::getUserId,userId);
        lambdaQueryWrapper.eq(SysTransferRecord::getMinerId,minerId);
        lambdaQueryWrapper.eq(SysTransferRecord::getType, CurrencyEnum.XCH.name());
        log.info("查询提币完成的记录");
        List<SysTransferRecord> transfers = sysTransferRecordMapper.selectList(lambdaQueryWrapper);
        log.info("提币完成的记录查询结果:{}",JSON.toJSONString(transfers));
        BigDecimal totalWithdraw = BigDecimal.ZERO;//已提取的收益
        BigDecimal drawing = BigDecimal.ZERO;//提取中的收益
        for(SysTransferRecord record : transfers ) {
            if(record.getStatus() == TransferRecordStatusEnum.FINISH.getStatus()){
                totalWithdraw = totalWithdraw.add(record.getAmount());
            }else if(record.getStatus() == TransferRecordStatusEnum.DRAWING.getStatus()){
                drawing = drawing.add(record.getAmount()).add(record.getFee());
            }
        }
        earningVo.setTotalWithdraw(BigDecimalUtil.formatFour(totalWithdraw).doubleValue());
        earningVo.setDrawingEarning(BigDecimalUtil.formatFour(drawing).doubleValue());
        return Result.success(earningVo);
    }
}

package com.mei.hui.miner.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.mei.hui.config.HttpRequestUtil;
import com.mei.hui.miner.common.MinerError;
import com.mei.hui.miner.entity.*;
import com.mei.hui.miner.mapper.MrAggWithdrawMapper;
import com.mei.hui.miner.mapper.SysMinerInfoMapper;
import com.mei.hui.miner.mapper.SysTransferRecordMapper;
import com.mei.hui.miner.model.EarningVo;
import com.mei.hui.miner.model.GetUserEarningInput;
import com.mei.hui.miner.model.SysTransferRecordWrap;
import com.mei.hui.miner.service.ISysTransferRecordService;
import com.mei.hui.miner.service.ISysVerifyCodeService;
import com.mei.hui.user.feign.feignClient.UserFeignClient;
import com.mei.hui.user.feign.vo.FindSysUserListInput;
import com.mei.hui.user.feign.vo.SysUserOut;
import com.mei.hui.util.BigDecimalUtil;
import com.mei.hui.util.ErrorCode;
import com.mei.hui.util.MyException;
import com.mei.hui.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.web.HateoasPageableHandlerMethodArgumentResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统划转记录Service业务层处理
 * 
 * @author ruoyi
 * @date 2021-03-08
 */
@Service
@Slf4j
public class SysTransferRecordServiceImpl implements ISysTransferRecordService
{
    @Autowired
    private SysTransferRecordMapper sysTransferRecordMapper;
    @Autowired
    private UserFeignClient userFeignClient;
    @Autowired
    private ISysVerifyCodeService sysVerifyCodeService;
    @Autowired
    private SysMinerInfoMapper sysMinerInfoMapper;
    @Autowired
    private MrAggWithdrawMapper mrAggWithdrawMapper;

    /**
     * 查询系统划转记录
     * 
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
        Long userId = sysTransferRecord.getUserId();
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
        LambdaQueryWrapper<MrAggWithdraw> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MrAggWithdraw::getSysUserId,userId);
        log.info("查询提现汇总表记录,userId={}",userId);
        List<MrAggWithdraw> aggWithdraws = mrAggWithdrawMapper.selectList(queryWrapper);
        log.info("查询提现汇总表记录,结果:{}",JSON.toJSONString(aggWithdraws));
        if(aggWithdraws.size() < 0){
            log.info("新增提现汇总信息");
            MrAggWithdraw insertAggWithdraw = MrAggWithdraw.builder().sysUserId(userId).takeTotalMony(transferRecord.getAmount())
                    .tatal_count(1).total_fee(transferRecord.getFee()).build();
            mrAggWithdrawMapper.insert(insertAggWithdraw);
        }else{
            log.info("更新提现汇总信息");
            MrAggWithdraw mrAggWithdraw = aggWithdraws.get(0);
            mrAggWithdraw.setTakeTotalMony(mrAggWithdraw.getTakeTotalMony().add(transferRecord.getAmount()));
            mrAggWithdraw.setTatal_count(mrAggWithdraw.getTatal_count() + 1);
            mrAggWithdraw.setTotal_fee(mrAggWithdraw.getTotal_fee().add(transferRecord.getFee()));
            mrAggWithdrawMapper.updateById(mrAggWithdraw);
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

    @Override
    public BigDecimal selectTotalEarning() {
        return sysTransferRecordMapper.selectTotalEarning();
    }

    @Override
    public BigDecimal selectTodayEarning() {
        return sysTransferRecordMapper.selectTodayEarning();
    }

    @Override
    public Map<String,Object> findTransferRecords(SysTransferRecord sysTransferRecord) {
        if (StringUtils.isEmpty(sysTransferRecord.getMinerId())) {
            throw MyException.fail(MinerError.MYB_222222.getCode(), "minerId不能为空值");
        }
        LambdaQueryWrapper<SysTransferRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysTransferRecord::getUserId,HttpRequestUtil.getUserId());
        queryWrapper.eq(SysTransferRecord::getMinerId,sysTransferRecord.getMinerId());
        IPage<SysTransferRecord> page = sysTransferRecordMapper.selectPage(new Page<>(sysTransferRecord.getPageNum(), sysTransferRecord.getPageSize()), queryWrapper);
        /**
         * 批量获取用户
         */
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
     *
     * @param sysTransferRecord 系统划转记录
     * @return 系统划转记录集合
     */
    @Override
    public Map<String,Object> selectSysTransferRecordListUserName(SysTransferRecord sysTransferRecord){
        LambdaQueryWrapper<SysTransferRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(SysTransferRecord::getStatus);
        IPage<SysTransferRecord> page = sysTransferRecordMapper.selectPage(new Page<>(sysTransferRecord.getPageNum(), sysTransferRecord.getPageSize()), queryWrapper);
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
    public Result withdraw(SysTransferRecordWrap sysTransferRecordWrap){
        if(sysTransferRecordWrap.getMinerId() == null){
            throw MyException.fail(MinerError.MYB_222222.getCode(),"请选择旷工");
        }
        /**
         * 一：提取金额 < 可提现金额 - 提币中 金额
         */
        //获取可提现金额
        SysMinerInfo sysMinerInfo = sysMinerInfoMapper.selectById(sysTransferRecordWrap.getMinerId());
        if(sysMinerInfo == null){
            throw MyException.fail(MinerError.MYB_222222.getCode(),"旷工不存在");
        }
        BigDecimal balanceMinerAvailable = sysMinerInfo.getBalanceMinerAvailable();

        //获取提币中的金额(注意手续费)
        LambdaQueryWrapper<SysTransferRecord> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(SysTransferRecord::getUserId, HttpRequestUtil.getUserId());
        queryWrapper.eq(SysTransferRecord::getStatus,0);
        List<SysTransferRecord> transferRecord = sysTransferRecordMapper.selectList(queryWrapper);
        BigDecimal gettingEarning = new BigDecimal(0);
        transferRecord.stream().forEach(v->{
            gettingEarning.add(v.getAmount());
            gettingEarning.add(v.getFee());
        });
        //提取金额 < 可提现金额 - （提币中金额+手续费）
        BigDecimal account = balanceMinerAvailable.subtract(gettingEarning).subtract(sysTransferRecordWrap.getAmount());
        if(account.compareTo(new BigDecimal(0)) < 0){
            throw MyException.fail(MinerError.MYB_222222.getCode(),"金额不够");
        }
        Result<SysUserOut> userResult = userFeignClient.getLoginUser();
        if(!ErrorCode.MYB_000000.getCode().equals(userResult.getCode())){
            throw MyException.fail(userResult.getCode(),userResult.getMsg());
        }
        SysUserOut user = userResult.getData();
        BigDecimal fee = user.getFeeRate().multiply(sysTransferRecordWrap.getAmount());
        sysTransferRecordWrap.setFee(fee);

        //1. 校验验证码, 如果校验成功, 将验证码设置为已使用
        Long userId = user.getUserId();
        SysVerifyCode sysVerifyCode = new SysVerifyCode();
        sysVerifyCode.setUserId(userId);
        sysVerifyCode.setVerifyCode(sysTransferRecordWrap.getVerifyCode());
        SysVerifyCode sysVerifyCodeRet = sysVerifyCodeService.selectSysVerifyCodeByUserIdAndVerifyCode(sysVerifyCode);
        if (sysVerifyCodeRet == null) {
            return Result.fail(MinerError.MYB_222222.getCode(),"验证码错误");
        }
        sysVerifyCodeRet.setStatus(1);
        sysVerifyCodeRet.setUpdateTime(LocalDateTime.now());
        sysVerifyCodeService.updateSysVerifyCode(sysVerifyCodeRet);

        //2. 验证通过后, 记录提币申请
        SysTransferRecord sysTransferRecord = new SysTransferRecord();
        BeanUtils.copyProperties(sysTransferRecordWrap, sysTransferRecord);
        sysTransferRecord.setUserId(userId);
        sysTransferRecord.setCreateTime(LocalDateTime.now());
        sysTransferRecord.setUpdateTime(LocalDateTime.now());
        sysTransferRecord.setStatus(0);
        sysTransferRecord.setMinerId(sysTransferRecordWrap.getMinerId());
        sysTransferRecord.setCreateTime(LocalDateTime.now());
        int rows = sysTransferRecordMapper.insertSysTransferRecord(sysTransferRecord);
        return rows > 0 ? Result.OK : Result.fail(MinerError.MYB_222222.getCode(),"失败");
    }

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
        log.info("查询提取记录");
        List<SysTransferRecord> transfers = sysTransferRecordMapper.selectList(lambdaQueryWrapper);
        log.info("提取记录查询结果:{}",JSON.toJSONString(transfers));

        //已提取收益
        BigDecimal totalWithdraw = BigDecimal.ZERO;
        for(SysTransferRecord record : transfers ) {
            totalWithdraw = totalWithdraw.add(record.getFee()).add(record.getAmount());
        }
        earningVo.setTotalWithdraw(BigDecimalUtil.formatFour(totalWithdraw).doubleValue());
        /**
         * 获取所有可提取币
         */
        earningVo.setTotalEarning(BigDecimalUtil.formatFour(miner.getTotalBlockAward()).doubleValue());
        earningVo.setTotalLockAward(BigDecimalUtil.formatFour(miner.getLockAward()).doubleValue());
        earningVo.setAvailableEarning(BigDecimalUtil.formatFour(miner.getBalanceMinerAvailable()).doubleValue());
        return Result.success(earningVo);
    }
}

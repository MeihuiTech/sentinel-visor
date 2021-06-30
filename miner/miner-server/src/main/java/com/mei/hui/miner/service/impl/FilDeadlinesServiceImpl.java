package com.mei.hui.miner.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mei.hui.miner.common.MinerError;
import com.mei.hui.miner.entity.FilDeadlines;
import com.mei.hui.miner.entity.SysMinerInfo;
import com.mei.hui.miner.feign.vo.ReportDeadlinesBO;
import com.mei.hui.miner.feign.vo.WindowBo;
import com.mei.hui.miner.mapper.FilDeadlinesMapper;
import com.mei.hui.miner.mapper.SysMinerInfoMapper;
import com.mei.hui.miner.model.FilDeadlinesListVO;
import com.mei.hui.miner.model.FilDeadlinesNinetySixVO;
import com.mei.hui.miner.service.FilDeadlinesService;
import com.mei.hui.util.MyException;
import com.mei.hui.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * filcoin 矿工窗口记录表 服务实现类
 * </p>
 *
 * @author 鲍红建
 * @since 2021-06-23
 */
@Slf4j
@Service
public class FilDeadlinesServiceImpl extends ServiceImpl<FilDeadlinesMapper, FilDeadlines> implements FilDeadlinesService {
    @Autowired
    private SysMinerInfoMapper minerInfoMapper;
    @Autowired
    private FilDeadlinesMapper filDeadlinesMapper;

    /**
     * localCurrent-本地标记的当前窗口编号；remoteCurrent-上报的当前窗口编号
     * 情况一：localCurrent <= remoteCurrent 说明 本轮次，网上的信息已经更改，本地需要更新
     * 情况二：localCurrent > remoteCurrent  说明 下一轮次开始，需要查询新一轮的数据
     * @param bo
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result reportDeadlines(ReportDeadlinesBO bo) {
        /**
         * 校验minerId 是否正确
         */
        checkMinerId(bo.getMinerId());
        //从数据库查询最后插入一轮子的当前窗口序号
        LambdaQueryWrapper<FilDeadlines> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FilDeadlines::getMinerId,bo.getMinerId());
        queryWrapper.eq(FilDeadlines::getIsCurrent,1);
        queryWrapper.orderByDesc(FilDeadlines::getCreateTime);
        IPage<FilDeadlines> page = this.page(new Page<>(0, 1), queryWrapper);
        log.info("查询本地库中矿工的当前窗口:{}", JSON.toJSONString(page.getRecords()));
        if(page.getRecords().size() == 0){
            log.info("数据库没有当前矿工窗口数据,新增窗口数据48条");
            saveDeadLine(bo,0);
            return Result.OK;
        }
        FilDeadlines localWindow = page.getRecords().get(0);
        Integer localWindowNo = localWindow.getDeadline();
        Integer remoteWindowNo = getRemoteWindowNo(bo);
        log.info("本地当前窗口编号:{},远程当前窗口编号:{}",localWindowNo,remoteWindowNo);
        if(localWindowNo <= remoteWindowNo){
            //更新
            log.info("更新窗口数据");
            updateDeadLine(bo);
        }else{
            //新增
            log.info("新增窗口数据");
            saveDeadLine(bo,localWindow.getSort()+1);
        }
        return Result.OK;
    }



    /*用户首页WindowPoSt的96个窗口*/
    @Override
    public Result selectFilDeadlinesNinetySixList(Long id) {
        SysMinerInfo miner = minerInfoMapper.selectSysMinerInfoById(id);
        log.info("矿工信息：【{}】",JSON.toJSON(miner));
        if (miner == null) {
            return null;
        }

        List<FilDeadlinesListVO> filDeadlinesList = filDeadlinesMapper.selectFilDeadlinesNinetySixList(miner.getMinerId());
        log.info("用户首页WindowPoSt的96个窗口出参：【{}】",JSON.toJSON(filDeadlinesList));
        if (filDeadlinesList == null || filDeadlinesList.size() < 1) {
            return Result.OK;
        }
        FilDeadlinesNinetySixVO filDeadlinesNinetySixVO = new FilDeadlinesNinetySixVO();
        //今天矿工窗口记录
        List<FilDeadlinesListVO> todayFilDeadlinesList = new ArrayList<>();
        //昨天矿工窗口记录
        List<FilDeadlinesListVO> yesterdayFilDeadlinesList = new ArrayList<>();
        //当前窗口序号
        Integer deadline;
        for (int i = 0;i<filDeadlinesList.size();i++) {
            if (i<48){
                todayFilDeadlinesList.add(filDeadlinesList.get(i));
                if (1 == filDeadlinesList.get(i).getIsCurrent()) {
                    deadline = filDeadlinesList.get(i).getDeadline();
                    filDeadlinesNinetySixVO.setDeadline(deadline);
                }
            } else {
                yesterdayFilDeadlinesList.add(filDeadlinesList.get(i));
            }
        }
        filDeadlinesNinetySixVO.setTodayFilDeadlinesList(todayFilDeadlinesList);
        filDeadlinesNinetySixVO.setYesterdayFilDeadlinesList(yesterdayFilDeadlinesList);
        return Result.success(filDeadlinesNinetySixVO);
    }


    /**
     * 校验minerId 是否正确
     * @param minerId
     */
    public void checkMinerId(String minerId){
        log.info("校验minerid是否正确");
        LambdaQueryWrapper<SysMinerInfo> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(SysMinerInfo::getMinerId,minerId);
        List<SysMinerInfo> list = minerInfoMapper.selectList(queryWrapper);
        log.info("查询矿工信息:{}",JSON.toJSONString(list));
        if(list.size() == 0){
            throw MyException.fail(MinerError.MYB_222222.getCode(),"minerId 错误");
        }
        log.info("minerid校验通过");
    }

    /**
     * 更新窗口信息
     * @param bo
     * @return
     */
    public boolean updateDeadLine(ReportDeadlinesBO bo){
        Map<Integer,WindowBo> map = new HashMap<>();
        bo.getWindows().stream().forEach(v->{
            map.put(v.getDeadline(),v);
        });
        //查询本地minerId 的窗口信息，正常是48条数据
        LambdaQueryWrapper<FilDeadlines> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FilDeadlines::getMinerId,bo.getMinerId());
        queryWrapper.orderByDesc(FilDeadlines::getCreateTime);
        IPage<FilDeadlines> page = this.page(new Page<>(0, 48), queryWrapper);
        log.info("查询本地minerId={}的窗口信息,正常是48条数据:{}",bo.getMinerId(),JSON.toJSONString(page.getRecords()));

        List<FilDeadlines> lt = page.getRecords().stream().map(v -> {
            FilDeadlines filDeadlines = new FilDeadlines();
            WindowBo windowBo = map.get(v.getDeadline());
            filDeadlines.setId(v.getId()).setSort(v.getSort()).setUpdateTime(LocalDateTime.now())
                    .setMinerId(v.getMinerId()).setSectorsFaults(windowBo.getSectorsFaults())
                    .setProvenPartitions(windowBo.getProvenPartitions()).setPartitions(windowBo.getPartitions())
                    .setDeadline(windowBo.getDeadline()).setIsCurrent(windowBo.getIsCurrent())
                    .setSectors(windowBo.getSectors());
            return filDeadlines;
        }).collect(Collectors.toList());
        return this.updateBatchById(lt);
    }

    public boolean saveDeadLine(ReportDeadlinesBO bo,long sort){
        List<FilDeadlines> list = bo.getWindows().stream().map(v -> {
            FilDeadlines filDeadlines = new FilDeadlines();
            BeanUtils.copyProperties(v, filDeadlines);
            filDeadlines.setMinerId(bo.getMinerId())
                    .setCreateTime(LocalDateTime.now())
                    .setUpdateTime(LocalDateTime.now())
                    .setSort(sort);
            return filDeadlines;
        }).collect(Collectors.toList());
        return this.saveBatch(list);
    }

    /**
     * 获取上报窗口的当前窗口编号
     * @param bo
     * @return
     */
    public Integer getRemoteWindowNo(ReportDeadlinesBO bo){
        for(WindowBo v : bo.getWindows()){
            if(v.getIsCurrent() == 1){
                return v.getDeadline();
            }
        }
        return null;
    }
}

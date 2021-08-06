package com.mei.hui.miner.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mei.hui.miner.common.MinerError;
import com.mei.hui.miner.entity.FilAdminUser;
import com.mei.hui.miner.feign.vo.AdminUserPageBO;
import com.mei.hui.miner.feign.vo.UpdateAdminUserBO;
import com.mei.hui.miner.manager.UserManager;
import com.mei.hui.miner.mapper.FilAdminUserMapper;
import com.mei.hui.miner.service.FilAdminUserService;
import com.mei.hui.user.feign.vo.SysUserOut;
import com.mei.hui.util.BasePage;
import com.mei.hui.util.MyException;
import com.mei.hui.util.PageResult;
import com.mei.hui.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 管理员和用户对应关系 服务实现类
 * </p>
 * @author 鲍红建
 * @since 2021-08-06
 */
@Service
@Slf4j
public class FilAdminUserServiceImpl extends ServiceImpl<FilAdminUserMapper, FilAdminUser> implements FilAdminUserService {
    @Autowired
    private UserManager userManager;

    public PageResult<AdminUserPageBO> adminUserPage(BasePage basePage){
        PageResult<SysUserOut> page = userManager.findAllAdminUser(basePage);
        log.info("查询管理员分页列表:{}", JSON.toJSONString(page.getRows()));
        List<Long> userIds = page.getRows().stream().map(v -> v.getUserId()).collect(Collectors.toList());
        Map<Long,String> map = new HashMap<>();
        page.getRows().stream().forEach(v->{
            map.put(v.getUserId(),v.getUserName());
        });

        LambdaQueryWrapper<FilAdminUser> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.in(FilAdminUser::getAdminId,userIds);
        List<FilAdminUser> list = this.list(queryWrapper);
        List<AdminUserPageBO> lt = list.stream().map(v -> {
            AdminUserPageBO adminUserPageBO = new AdminUserPageBO();
            adminUserPageBO.setAdminId(v.getAdminId());
            adminUserPageBO.setUserId(v.getUserId());
            adminUserPageBO.setAdminName(map.get(v.getAdminId()));
            adminUserPageBO.setUserName(map.get(v.getUserId()));
            return adminUserPageBO;
        }).collect(Collectors.toList());
        return new PageResult(page.getTotal(),lt);
    }

    public Result saveOrUpdateAdmin(@RequestBody UpdateAdminUserBO bo){
        LambdaQueryWrapper<FilAdminUser> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(FilAdminUser::getAdminId,bo.getAdminId());
        queryWrapper.eq(FilAdminUser::getUserId,bo.getUserId());
        List<FilAdminUser> list = this.list(queryWrapper);
        log.info("管理员管理的普通用户:{}",JSON.toJSONString(list));
        if(list.size() <= 0){
            FilAdminUser adminUser = new FilAdminUser();
            adminUser.setAdminId(bo.getAdminId());
            adminUser.setUserId(bo.getUserId());
            adminUser.setCreateTime(LocalDateTime.now());
            this.save(adminUser);
        }else{
            this.removeById(list.get(0).getId());
        }
        return Result.OK;
    }

}

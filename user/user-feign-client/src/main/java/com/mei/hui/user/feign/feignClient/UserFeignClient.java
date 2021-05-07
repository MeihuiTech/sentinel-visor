package com.mei.hui.user.feign.feignClient;

import com.mei.hui.user.feign.fallBackFactory.UserFeignFallbackFactory;
import com.mei.hui.user.feign.vo.FindSysUserListInput;
import com.mei.hui.user.feign.vo.SysUserOut;
import com.mei.hui.util.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;

/**
*@Description:
*@Author: 鲍红建
*@date: 2021/1/6
*/
@FeignClient(name = "user-server",path = "/system/user",fallbackFactory = UserFeignFallbackFactory.class )
public interface UserFeignClient {

    /**
     *
     * @param sysUserOut
     * @return
     */
    @PostMapping("/getUserById")
    Result<SysUserOut> getUserById(@RequestBody SysUserOut sysUserOut);

    /**
     * 批量获取用户
     * @param req
     * @return
     */
    @RequestMapping(value = "/findSysUserList",method = RequestMethod.POST)
    Result<List<SysUserOut>> findSysUserList(@RequestBody  FindSysUserListInput req);

    /**
     * 获取当前登陆用户
     * @return
     */
    @PostMapping("/getLoginUser")
     Result<SysUserOut> getLoginUser();
}

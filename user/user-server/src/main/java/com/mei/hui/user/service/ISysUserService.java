package com.mei.hui.user.service;

import com.mei.hui.user.entity.SysUser;
import com.mei.hui.user.feign.vo.FindSysUserListInput;
import com.mei.hui.user.feign.vo.SysUserOut;
import com.mei.hui.user.model.LoginBody;
import com.mei.hui.user.model.SelectUserListInput;
import com.mei.hui.util.Result;
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface ISysUserService {

    Map<String,Object> getSysUserByNameAndPass(LoginBody loginBody);
    String selectUserRoleGroup(String userName);

    Map<String,Object> selectUserList(SelectUserListInput user);

    SysUser selectUserById(Long userId);

    String checkUserNameUnique(String userName);

    String checkPhoneUnique(SysUser user);

    String checkEmailUnique(SysUser user);

    int insertUser(SysUser user);
    int updateUser(SysUser user);

    void checkUserAllowed(SysUser user);

    int deleteUserByIds(Long[] userIds);

    int resetPwd(SysUser user);

    int updateUserStatus(SysUser user);

    Result<List<SysUserOut>> findSysUserList(FindSysUserListInput req);

    SysUser getLoginUser();

    SysUser getUserById(Long userId);

    boolean updateUserAvatar(Long userId, String avatar);

    Map<String,Object> Impersonation(Long userId);
}

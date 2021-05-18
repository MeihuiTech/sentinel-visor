package com.mei.hui.miner.SystemController;

import com.mei.hui.miner.model.SysCurrencyVO;
import com.mei.hui.miner.service.ISysCurrencyService;
import com.mei.hui.util.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 币种表
 * @author shangbin
 * @version v1.0.0
 * @date
 **/
@Slf4j
@Api(value = "币种表",tags = "币种表")
@RestController
@RequestMapping("/system/currency")
public class SysCurrencyController {

    @Autowired
    private ISysCurrencyService sysCurrencyService;

    /**
    * 不分页排序查询币种列表
    *
    * @description
    * @author shangbin
    * @date 2021/5/14 14:52
    * @param []
    * @return com.mei.hui.util.Result
    * @version v1.0.0
    */
    @ApiOperation(value = "不分页排序查询币种列表")
    @GetMapping("/list")
    public Result listCurrency(){
        List<SysCurrencyVO> sysCurrencyVOList = sysCurrencyService.listCurrency();
        return Result.success(sysCurrencyVOList);
    }


}

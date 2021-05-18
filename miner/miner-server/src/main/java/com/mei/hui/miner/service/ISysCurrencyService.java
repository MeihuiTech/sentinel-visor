package com.mei.hui.miner.service;

import com.mei.hui.miner.model.SysCurrencyVO;

import java.util.List;

/**
 * 币种表
 * @author shangbin
 * @version v1.0.0
 * @date 2021/5/14 14:46
 **/
public interface ISysCurrencyService {


    /**
    * 不分页排序查询币种列表
    *
    * @description
    * @author shangbin
    * @date 2021/5/14 14:57
    * @param []
    * @return java.util.List<com.mei.hui.miner.model.SysCurrencyVO>
    * @version v1.0.0
    */
    public List<SysCurrencyVO> listCurrency();
}

package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Override
    public List<ShopType> queryType(){
        String key= RedisConstants.CACHE_SHOPTYPE_KEY;
        String json= stringRedisTemplate.opsForValue().get(key);
        if(json==null||json.isEmpty()){
            List<ShopType> shopTypeList= query().orderByAsc("sort").list();
            String shop_type_str = JSONUtil.toJsonStr(shopTypeList);
            stringRedisTemplate.opsForValue().set(key,shop_type_str);
            return shopTypeList;
        }
        else return JSONUtil.toList(json,ShopType.class
        );
    }
}

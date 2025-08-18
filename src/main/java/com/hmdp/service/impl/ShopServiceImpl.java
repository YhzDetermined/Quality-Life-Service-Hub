package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
//       缓存穿透
//        Shop shop=cacheClient
//                .queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
//        互斥锁解决缓存击穿
//        Shop shop=queryWithMutex(id);
//        逻辑过期解决缓存击穿
        Shop shop=cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if(shop==null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

/*    private static final ExecutorService CACHE_REBULIDER_EXECUTOR= Executors.newFixedThreadPool(10);
    public Shop queryWithLogicalExpire(Long id){
        String key= RedisConstants.CACHE_SHOP_KEY +id;
//        1.从redis查询商户缓存
        String shopJson=stringRedisTemplate.opsForValue().get(key);
//        2.判断是否存在
        if(StrUtil.isBlank(shopJson)){
            //        3.存在，直接返回
            return null;
        }
//        4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop=JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
        LocalDateTime expireTime =redisData.getExpireTime();
//        5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //        5.1.未过期，直接返回店铺信息
            return shop;
        }
//        5.2.已过期，需要缓存重建
//        6.缓存重建
//        6.1.获取互斥锁
        String lockKey=LOCK_SHOP_KEY+id;
        boolean isLock=tryLock(lockKey);
//        6.2.判断是否获取锁成功
        if(isLock){
//        6.3.成功，开启独立线程，实现缓存重建
            CACHE_REBULIDER_EXECUTOR.submit(()->{
//            重建缓存
                try {
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
//                释放锁

            });
        }
//
//        6.4.返回过期的店铺信息
        return shop;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    public Shop queryWithMutex(Long id){
        String key= RedisConstants.CACHE_SHOP_KEY +id;
//        1.从redis查询商户缓存
        String shopJson=stringRedisTemplate.opsForValue().get(key);
//        2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //        3.存在，直接返回
            return JSONUtil.toBean(shopJson,Shop.class);
        }
//        判断命中的是否是空值
        if(shopJson !=null){
            return null;
        }
//        4.实现缓存重建
//        4.1获取互斥锁
        String lockKey="lock:shop"+id;
        Shop shop= null;
        try {
            boolean isLock = tryLock(lockKey);
//        4.2判断是否成功
            if(!isLock){
                //        4.3失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

//        4.4成功，根据id查询数据库
            shop = getById(id);
//            模拟重建的延时
            Thread.sleep(200);

//        5.不存在，返回错误
            if(shop==null){
    //            将空值写入redis
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
//        6.存在，写入redis
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
//        7.释放互斥锁
        unlock(lockKey);
//        8.返回
        return shop;
    }
    public Shop queryWithPassThrough(Long id){
        String key= RedisConstants.CACHE_SHOP_KEY +id;
//        1.从redis查询商户缓存
        String shopJson=stringRedisTemplate.opsForValue().get(key);
//        2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //        3.存在，直接返回
            return JSONUtil.toBean(shopJson,Shop.class);
        }
//        判断命中的是否是空值
        if(shopJson !=null){
            return null;
        }
//        4.不存在，根据id查询数据库
        Shop shop=getById(id);
//        5.不存在，返回错误
        if(shop==null){
//            将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
//        6.存在，写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        7.返回
        return shop;
    }*/
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id=shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空");
        }
//        1.更新数据库
        updateById(shop);
//        2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
//        1.判断是否需要根据坐标查询
        if(x == null || y == null){
            //        // 根据类型分页查询
        Page<Shop> page = query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
        }
//        2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current*SystemConstants.DEFAULT_PAGE_SIZE;
//        3.查询redis、按照距离排序、分页 结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()  //GEOSEARCH key BYLONAT x y BYRADIS 10 WITHDISTANCE
                .search(key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        if(results == null){
            return Result.ok();
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if(list.size() <= from)
            return Result.ok();
        List<Long> ids = new ArrayList<>(list.size());
        Map<String,Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result->{
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr,distance);
        });
//        4.解析出id
        String idStr = StrUtil.join(",",ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for(Shop shop:shops){
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
//        5.根据id查询shop
        return Result.ok(shops);
    }

/*    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
//        1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //        2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        3.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }*/
}

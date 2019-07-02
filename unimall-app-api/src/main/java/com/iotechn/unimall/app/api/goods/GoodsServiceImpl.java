package com.iotechn.unimall.app.api.goods;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.iotechn.unimall.core.Const;
import com.iotechn.unimall.core.exception.ServiceException;
import com.iotechn.unimall.data.component.CacheComponent;
import com.iotechn.unimall.data.domain.CategoryDO;
import com.iotechn.unimall.data.domain.ImgDO;
import com.iotechn.unimall.data.domain.SkuDO;
import com.iotechn.unimall.data.domain.SpuDO;
import com.iotechn.unimall.data.dto.SpuDTO;
import com.iotechn.unimall.data.enums.BizType;
import com.iotechn.unimall.data.mapper.CategoryMapper;
import com.iotechn.unimall.data.mapper.ImgMapper;
import com.iotechn.unimall.data.mapper.SkuMapper;
import com.iotechn.unimall.data.mapper.SpuMapper;
import com.iotechn.unimall.data.model.Page;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Created by rize on 2019/7/2.
 */
@Service
public class GoodsServiceImpl implements GoodsService {

    @Autowired
    private SpuMapper spuMapper;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private ImgMapper imgMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private CacheComponent cacheComponent;

    private static final String CA_SPU_PAGE_PREFIX = "CA_SPU_PAGE_";

    private static final String CA_SPU_PREFIX = "CA_SPU_";

    @Override
    public Page<SpuDTO> getGoodsPage(Integer pageNo, Integer pageSize, Long categoryId, String title) throws ServiceException {
        Wrapper<SpuDO> wrapper = new EntityWrapper<SpuDO>();
        if (!StringUtils.isEmpty(title)) {
            wrapper.like("title", title);
        } else {
            //若关键字为空，尝试从缓存取列表
            return cacheComponent.getObj(CA_SPU_PAGE_PREFIX + categoryId + "_" + pageNo + "_" + pageSize, Page.class);
        }
        if (categoryId != null) {
            List<CategoryDO> childrenList = categoryMapper.selectList(new EntityWrapper<CategoryDO>().eq("parent_id", categoryId));
            if (CollectionUtils.isEmpty(childrenList)) {
                //目标节点为叶子节点
                wrapper.eq("category_id", categoryId);
            } else {
                //目标节点存在子节点
                LinkedList<Long> childrenIds = new LinkedList<>();
                CategoryDO categoryDO = categoryMapper.selectById(categoryId);
                if (categoryDO.getParentId() != 0) {
                    //二级分类
                    childrenList.forEach(item -> {
                        childrenIds.add(item.getId());
                    });
                } else {
                    //一级分类
                    childrenList.forEach(item -> {
                        List<CategoryDO> leafList = categoryMapper.selectList(new EntityWrapper<CategoryDO>().eq("parent_id", item.getParentId()));
                        if (!CollectionUtils.isEmpty(leafList)) {
                            leafList.forEach(leafItem -> {
                                childrenIds.add(leafItem.getId());
                            });
                        }
                    });
                }
                wrapper.in("category_id", childrenIds);
            }
        }
        //TODO 设置枚举
        wrapper.eq("status", 1);
        List<SpuDO> spuDOS = spuMapper.selectPage(new RowBounds((pageNo - 1) * pageSize, pageSize), wrapper);
        //组装SPU
        List<SpuDTO> spuDTOList = new ArrayList<>();
        spuDOS.forEach(item -> {
            SpuDTO spuDTO = new SpuDTO();
            BeanUtils.copyProperties(item, spuDTO);
            //TODO 销量\缓存用hash表把
            spuDTOList.add(spuDTO);
        });

        Integer count = spuMapper.selectCount(wrapper);
        Page<SpuDTO> page = new Page<>(spuDTOList, pageNo, pageSize, count);
        if (StringUtils.isEmpty(title)) {
            //若关键字为空，制作缓存
            cacheComponent.putObj(CA_SPU_PAGE_PREFIX + categoryId + "_" + pageNo + "_" + pageSize, page, Const.CACHE_ONE_DAY);
        }
        return page;
    }

    @Override
    public SpuDTO getGoods(Long spuId) throws ServiceException {
        SpuDTO spuDTOFromCache = cacheComponent.getObj(CA_SPU_PREFIX + spuId, SpuDTO.class);
        if (spuDTOFromCache != null) {
            return spuDTOFromCache;
        }
        SpuDO spuDO = spuMapper.selectById(spuId);
        SpuDTO spuDTO = new SpuDTO();
        BeanUtils.copyProperties(spuDO, spuDTO);
        List<ImgDO> imgDOS = imgMapper.selectList(
                new EntityWrapper<ImgDO>()
                        .eq("biz_type", BizType.GOODS.getCode())
                        .eq("biz_id", spuId));
        //TODO 直接SQL查出来URL
        spuDTO.setImgList(imgDOS.stream().map(imgDO -> imgDO.getUrl()).collect(Collectors.toList()));
        List<SkuDO> skuDOList = skuMapper.selectList(
                new EntityWrapper<SkuDO>()
                        .eq("spu_id", spuId));
        spuDTO.setSkuList(skuDOList);
        //放入缓存
        cacheComponent.putObj(CA_SPU_PREFIX + spuId, spuDTO, Const.CACHE_ONE_DAY);
        return spuDTO;
    }
}
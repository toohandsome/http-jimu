package com.jimu.http.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jimu.http.entity.HttpJimuGroup;
import com.jimu.http.mapper.HttpJimuConfigMapper;
import com.jimu.http.mapper.HttpJimuGroupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HttpJimuGroupService extends ServiceImpl<HttpJimuGroupMapper, HttpJimuGroup> {

    private final HttpJimuConfigMapper configMapper;

    @Override
    @Transactional
    public boolean saveOrUpdate(HttpJimuGroup entity) {
        if (entity != null && entity.getCode() != null) {
            entity.setCode(entity.getCode().trim());
        }
        if (entity != null && entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }
        if (entity != null && entity.getCode() != null && !entity.getCode().isBlank()) {
            long count = this.count(new LambdaQueryWrapper<HttpJimuGroup>()
                    .eq(HttpJimuGroup::getCode, entity.getCode())
                    .ne(entity.getId() != null, HttpJimuGroup::getId, entity.getId()));
            if (count > 0) {
                throw new IllegalArgumentException("group code already exists: " + entity.getCode());
            }
        }
        return super.saveOrUpdate(entity);
    }

    @Override
    @Transactional
    public boolean removeById(java.io.Serializable id) {
        boolean success = super.removeById(id);
        if (success) {
            configMapper.update(null, new LambdaUpdateWrapper<com.jimu.http.entity.HttpJimuConfig>()
                    .set(com.jimu.http.entity.HttpJimuConfig::getGroupId, null)
                    .eq(com.jimu.http.entity.HttpJimuConfig::getGroupId, String.valueOf(id)));
        }
        return success;
    }
}

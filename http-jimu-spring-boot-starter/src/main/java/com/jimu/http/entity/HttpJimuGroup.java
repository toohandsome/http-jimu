package com.jimu.http.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("http_jimu_group")
public class HttpJimuGroup {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String code;
    private String name;
    private String description;
    private String stepsConfig;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

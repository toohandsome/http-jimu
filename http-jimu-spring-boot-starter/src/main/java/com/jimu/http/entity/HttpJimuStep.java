package com.jimu.http.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("http_jimu_step")
public class HttpJimuStep {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String code;
    private String name;
    private String type; // SORT, SIGN, ENCRYPT, ADD_FIXED, SCRIPT
    private String target; // HEADER, BODY, QUERY, FORM, RESPONSE_BODY, RESPONSE_HEADER, RESPONSE_STATUS
    private String scriptContent;
    private String configJson;
    private String inputSchema;
    private String outputSchema;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
